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
import org.springframework.jdbc.core.JdbcTemplate;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.ErrorCode;
import tp.appsinter.cine.model.dto.request.CreateMovieScreeningDTO;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.CreateSeatHoldDTO;
import tp.appsinter.cine.model.dto.request.CreateSeatRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.model.entities.*;
import tp.appsinter.cine.model.utils.*;
import tp.appsinter.cine.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Seat booking")
class BookingFlowTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private MovieScreeningService movieScreeningService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatHoldService seatHoldService;
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
        DatabaseCleaner.truncate(jdbcTemplate);

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
                    // Los identificadores nuevos son UUIDv7: ordenados por creación y amigables con el índice.
                    () -> assertThat(response.uuid().version()).isEqualTo(7),
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

        @Test
        @DisplayName("refuses a customer cancellation once the screening has started, but lets an admin through")
        void refusesCancellationAfterStart() {
            CreateReservationResponseDTO created = reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of()));
            screening.setStartsAt(OffsetDateTime.now().minusMinutes(10));
            movieScreeningRepository.save(screening);

            assertThatThrownBy(() -> reservationService.cancelReservation(created.uuid(), customer.getEmail(), false))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCREENING_STARTED));

            assertThat(reservationService.cancelReservation(created.uuid(), "admin@example.com", true).status())
                    .isEqualTo(ReservationStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("when editing a sold seat")
    class EditSoldSeat {

        @Test
        @DisplayName("refuses to move it but still allows changing its type")
        void protectsTicketHistory() {
            reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of()));
            UUID roomId = screening.getScreenRoom().getUuid();

            assertThatThrownBy(() -> seatService.update(normalSeat.getUuid(),
                    new CreateSeatRequestDTO(roomId, "Z", "9", SeatType.NORMAL)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE));

            assertThat(seatService.update(normalSeat.getUuid(),
                    new CreateSeatRequestDTO(roomId, "a", " 1 ", SeatType.PREMIUM)).seatType())
                    .isEqualTo(SeatType.PREMIUM);
        }
    }

    @Nested
    @DisplayName("seat holds by other customers")
    class HoldsByOthers {

        private User other;

        @BeforeEach
        void createOtherCustomer() {
            other = userRepository.save(User.builder()
                    .firstName("Grace")
                    .lastName("Hopper")
                    .email("grace@example.com")
                    .password("{noop}secret")
                    .role(Role.CUSTOMER)
                    .build());
        }

        @Test
        @DisplayName("are subtracted from screening availability, except for their owner")
        void reduceAvailability() {
            seatHoldService.create(screening.getUuid(), other.getEmail(),
                    new CreateSeatHoldDTO(List.of(normalSeat.getUuid())));

            var anonymous = movieScreeningService.findById(screening.getUuid(), true, null);
            assertAll(
                    () -> assertThat(anonymous.totalSeats()).isEqualTo(2),
                    () -> assertThat(anonymous.availableSeats()).isEqualTo(1),
                    () -> assertThat(anonymous.heldSeats()).isEqualTo(1),
                    () -> assertThat(anonymous.soldOut()).isFalse());

            var owner = movieScreeningService.findById(screening.getUuid(), true, other.getEmail());
            assertAll(
                    () -> assertThat(owner.availableSeats()).isEqualTo(2),
                    () -> assertThat(owner.heldSeats()).isZero());
        }

        @Test
        @DisplayName("make the quote fail just like the purchase would")
        void blockQuotes() {
            seatHoldService.create(screening.getUuid(), other.getEmail(),
                    new CreateSeatHoldDTO(List.of(normalSeat.getUuid())));

            assertThatThrownBy(() -> reservationService.quote(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of())))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SEATS_UNAVAILABLE));
        }

        @Test
        @DisplayName("let their owner quote with the hold without consuming it")
        void quoteWithOwnHold() {
            var hold = seatHoldService.create(screening.getUuid(), other.getEmail(),
                    new CreateSeatHoldDTO(List.of(normalSeat.getUuid())));

            var quote = reservationService.quote(other.getEmail(), new CreateReservationDTO(
                    screening.getUuid(), List.of(normalSeat.getUuid()), List.of(), hold.uuid()));

            assertThat(quote.totalPrice()).isEqualByComparingTo("10.00");
            assertThat(seatHoldService.findById(hold.uuid(), other.getEmail()).uuid()).isEqualTo(hold.uuid());
        }
    }

    @Nested
    @DisplayName("when rescheduling a screening")
    class RescheduleScreening {

        @Test
        @DisplayName("refuses to create a screening in the past")
        void refusesPastStart() {
            assertThatThrownBy(() -> movieScreeningService.create(new CreateMovieScreeningDTO(
                    screening.getMovie().getUuid(), screening.getScreenRoom().getUuid(),
                    OffsetDateTime.now().minusHours(1), new BigDecimal("10.00"))))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        @DisplayName("lets a started screening change only its price")
        void editsStartedScreeningPrice() {
            screening.setStartsAt(OffsetDateTime.now().minusMinutes(10));
            movieScreeningRepository.save(screening);
            UUID movieId = screening.getMovie().getUuid();
            UUID roomId = screening.getScreenRoom().getUuid();
            OffsetDateTime startsAt = movieScreeningRepository.findById(screening.getUuid()).orElseThrow().getStartsAt();

            assertThat(movieScreeningService.update(screening.getUuid(), new CreateMovieScreeningDTO(
                    movieId, roomId, startsAt, new BigDecimal("8.00"))).basePrice())
                    .isEqualByComparingTo("8.00");

            assertThatThrownBy(() -> movieScreeningService.update(screening.getUuid(), new CreateMovieScreeningDTO(
                    movieId, roomId, OffsetDateTime.now().plusDays(3), new BigDecimal("8.00"))))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCREENING_STARTED));
        }

        @Test
        @DisplayName("refuses to move a screening with tickets sold but still allows a price change")
        void protectsSoldScreenings() {
            reservationService.createReservation(customer.getEmail(),
                    new CreateReservationDTO(screening.getUuid(), List.of(normalSeat.getUuid()), List.of()));
            UUID movieId = screening.getMovie().getUuid();
            UUID roomId = screening.getScreenRoom().getUuid();
            // Se relee de la base para comparar con la precisión que guarda Postgres (microsegundos).
            OffsetDateTime startsAt = movieScreeningRepository.findById(screening.getUuid()).orElseThrow().getStartsAt();

            assertThatThrownBy(() -> movieScreeningService.update(screening.getUuid(), new CreateMovieScreeningDTO(
                    movieId, roomId, startsAt.plusHours(5), new BigDecimal("10.00"))))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE));

            assertThat(movieScreeningService.update(screening.getUuid(), new CreateMovieScreeningDTO(
                    movieId, roomId, startsAt, new BigDecimal("12.00"))).basePrice())
                    .isEqualByComparingTo("12.00");
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
                    reservationService.findSeatMap(screening.getUuid(), null).seats();

            assertThat(availability)
                    .extracting(SeatAvailabilityResponseDTO::seatId, SeatAvailabilityResponseDTO::available)
                    .containsExactlyInAnyOrder(
                            Tuple.tuple(normalSeat.getUuid(), true),
                            Tuple.tuple(premiumSeat.getUuid(), false));
        }
    }
}
