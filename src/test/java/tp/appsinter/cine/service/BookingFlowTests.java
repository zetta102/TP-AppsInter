package tp.appsinter.cine.service;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.model.entities.*;
import tp.appsinter.cine.model.utils.*;
import tp.appsinter.cine.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Seat booking")
class BookingFlowTests {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ScreenRoomRepository screenRoomRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private MovieScreeningRepository movieScreeningRepository;
    @Autowired
    private UserRepository userRepository;

    private User customer;
    private MovieScreening screening;
    private Seat normalSeat;
    private Seat premiumSeat;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        movieScreeningRepository.deleteAll();
        seatRepository.deleteAll();
        screenRoomRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        customer = userRepository.save(User.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .password("{noop}secret")
                .role(Role.CUSTOMER)
                .build());

        Movie movie = movieRepository.save(Movie.builder()
                .movieName("Metropolis")
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(120)
                .mainCast(new ArrayList<>(List.of("Brigitte Helm")))
                .build());

        ScreenRoom room = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 1")
                .screenRoomType(ScreenRoomType.IMAX)
                .build());

        normalSeat = seatRepository.save(Seat.builder()
                .position(new Position("A", "1"))
                .seatType(SeatType.NORMAL)
                .screenRoom(room)
                .build());
        premiumSeat = seatRepository.save(Seat.builder()
                .position(new Position("A", "2"))
                .seatType(SeatType.PREMIUM)
                .screenRoom(room)
                .build());

        screening = movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(OffsetDateTime.now().plusDays(1))
                .endsAt(OffsetDateTime.now().plusDays(1).plusMinutes(135))
                .basePrice(new BigDecimal("10.00"))
                .build());
    }

    @Test
    @DisplayName("database rejects two tickets for the same screening and seat")
    void databaseRejectsDuplicateTicket() {
        Reservation first = persistReservationWithTicket();
        assertThat(first.getUuid()).isNotNull();

        assertThatThrownBy(this::persistReservationWithTicket)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Reservation persistReservationWithTicket() {
        Reservation reservation = Reservation.builder()
                .user(customer)
                .movieScreening(screening)
                .status(ReservationStatus.CONFIRMED)
                .totalPrice(new BigDecimal("10.00"))
                .createdAt(OffsetDateTime.now())
                .build();
        reservation.addTicket(Ticket.builder()
                .movieScreening(screening)
                .seat(normalSeat)
                .seatType(SeatType.NORMAL)
                .price(new BigDecimal("10.00"))
                .build());
        return reservationRepository.saveAndFlush(reservation);
    }

    @Nested
    @DisplayName("when creating a reservation")
    class CreateReservation {

        @Test
        @DisplayName("books the requested seats and prices them by seat type")
        void booksRequestedSeats() {
            CreateReservationResponseDTO response = reservationService.createReservation(
                    customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid(), premiumSeat.getUuid()), List.of()));

            assertAll(
                    () -> assertThat(response.tickets()).hasSize(2),
                    () -> assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED),
                    () -> assertThat(response.totalPrice()).isEqualByComparingTo(new BigDecimal("25.00")),
                    () -> assertThat(ticketRepository.findBookedSeatIds(screening.getUuid()))
                            .containsExactlyInAnyOrder(normalSeat.getUuid(), premiumSeat.getUuid()));
        }

        @Test
        @DisplayName("rejects a seat that is already booked for the same screening")
        void rejectsAlreadyBookedSeat() {
            reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of()));

            assertThatThrownBy(() -> reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of())))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("rejects seats that belong to a different room")
        void rejectsSeatFromAnotherRoom() {
            ScreenRoom otherRoom = screenRoomRepository.save(ScreenRoom.builder()
                    .roomName("Room 2")
                    .screenRoomType(ScreenRoomType.NORMAL)
                    .build());
            Seat foreignSeat = seatRepository.save(Seat.builder()
                    .position(new Position("B", "1"))
                    .seatType(SeatType.NORMAL)
                    .screenRoom(otherRoom)
                    .build());

            assertThatThrownBy(() -> reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(foreignSeat.getUuid()), List.of())))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("when cancelling a reservation")
    class CancelReservation {

        @Test
        @DisplayName("releases the seats so they can be booked again")
        void releasesSeats() {
            CreateReservationResponseDTO created = reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of()));

            CreateReservationResponseDTO cancelled =
                    reservationService.cancelReservation(created.uuid(), customer.getEmail(), false);

            assertAll(
                    () -> assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED),
                    () -> assertThat(ticketRepository.findBookedSeatIds(screening.getUuid())).isEmpty());

            assertThat(reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of())).tickets())
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("seat availability")
    class SeatAvailability {

        @Test
        @DisplayName("marks booked seats as unavailable")
        void marksBookedSeats() {
            reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(premiumSeat.getUuid()), List.of()));

            List<SeatAvailabilityResponseDTO> availability =
                    reservationService.findSeatMap(screening.getUuid()).seats();

            assertThat(availability)
                    .extracting(SeatAvailabilityResponseDTO::seatId, SeatAvailabilityResponseDTO::available)
                    .containsExactlyInAnyOrder(
                            Tuple.tuple(normalSeat.getUuid(), true),
                            Tuple.tuple(premiumSeat.getUuid(), false));
        }
    }
}
