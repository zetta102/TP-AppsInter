package tp.appsinter.cine.reservation;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tp.appsinter.cine.hold.SeatHoldService;
import tp.appsinter.cine.hold.dto.request.SeatHoldRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.reservation.dto.response.ReservationResponseDTO;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatService;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.room.dto.request.SeatRequestDTO;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.screening.MovieScreeningService;
import tp.appsinter.cine.screening.SeatMapService;
import tp.appsinter.cine.screening.dto.request.ScreeningRequestDTO;
import tp.appsinter.cine.screening.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.MutableClock;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.OTHER_CUSTOMER;

/**
 * Reglas de compra a nivel de servicio, sin pasar por HTTP.
 */
@ApiIntegrationTest
@DisplayName("Seat booking")
class BookingFlowTests {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private MovieScreeningService movieScreeningService;
    @Autowired
    private SeatMapService seatMapService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatHoldService seatHoldService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private MovieScreeningRepository movieScreeningRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private MutableClock clock;

    private Catalogue catalogue;
    private MovieScreening screening;
    private Seat normalSeat;
    private Seat premiumSeat;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL, SeatType.PREMIUM);
        screening = catalogue.screening();
        normalSeat = catalogue.seat(1);
        premiumSeat = catalogue.seat(2);
    }

    private ReservationRequestDTO request(Seat... seats) {
        return new ReservationRequestDTO(screening.getUuid(), Arrays.stream(seats).map(Seat::getUuid).toList(),
                List.of());
    }

    private ReservationResponseDTO book(Seat... seats) {
        return reservationService.createReservation(CUSTOMER, request(seats));
    }

    /**
     * Lleva el reloj a diez minutos después del comienzo de la función.
     */
    private void startScreening() {
        clock.advance(Duration.between(fixture.now(), screening.getStartsAt()).plusMinutes(10));
    }

    private OffsetDateTime storedStart() {
        // Se relee de la base para comparar con la precisión que guarda Postgres (microsegundos).
        return movieScreeningRepository.findById(screening.getUuid()).orElseThrow().getStartsAt();
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
                .user(userRepository.findByEmailIgnoreCase(CUSTOMER).orElseThrow())
                .movieScreening(screening)
                .status(ReservationStatus.CONFIRMED)
                .totalPrice(new BigDecimal("10.00"))
                .createdAt(fixture.now())
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
            ReservationResponseDTO response = book(normalSeat, premiumSeat);

            assertSoftly(softly -> {
                softly.assertThat(response.tickets()).hasSize(2);
                softly.assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
                // Los identificadores nuevos son UUIDv7: ordenados por creación y amigables con el índice.
                softly.assertThat(response.uuid().version()).isEqualTo(7);
                softly.assertThat(response.totalPrice()).isEqualByComparingTo("25.00");
                softly.assertThat(ticketRepository.findBookedSeatIds(screening.getUuid()))
                        .containsExactlyInAnyOrder(normalSeat.getUuid(), premiumSeat.getUuid());
            });
        }

        @Test
        @DisplayName("rejects a seat that is already booked for the same screening")
        void rejectsAlreadyBookedSeat() {
            book(normalSeat);

            assertThatThrownBy(() -> book(normalSeat))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SEATS_UNAVAILABLE));
        }

        @Test
        @DisplayName("rejects seats that belong to a different room")
        void rejectsSeatFromAnotherRoom() {
            ScreenRoom otherRoom = fixture.room("Room 2");
            Seat foreignSeat = fixture.seat(otherRoom, "B", "1", SeatType.NORMAL);

            assertThatThrownBy(() -> book(foreignSeat))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SEATS_WRONG_ROOM));
        }
    }

    @Nested
    @DisplayName("when cancelling a reservation")
    class CancelReservation {

        @Test
        @DisplayName("releases the seats so they can be booked again")
        void releasesSeats() {
            ReservationResponseDTO created = book(normalSeat);

            ReservationResponseDTO cancelled = reservationService.cancelReservation(created.uuid(), CUSTOMER, false);

            assertSoftly(softly -> {
                softly.assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
                softly.assertThat(ticketRepository.findBookedSeatIds(screening.getUuid())).isEmpty();
            });
            assertThat(book(normalSeat).tickets()).hasSize(1);
        }

        @Test
        @DisplayName("refuses a customer cancellation once the screening has started, but lets an admin through")
        void refusesCancellationAfterStart() {
            ReservationResponseDTO created = book(normalSeat);
            startScreening();

            assertThatThrownBy(() -> reservationService.cancelReservation(created.uuid(), CUSTOMER, false))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCREENING_STARTED));

            assertThat(reservationService.cancelReservation(created.uuid(), ADMIN, true).status())
                    .isEqualTo(ReservationStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("when editing a sold seat")
    class EditSoldSeat {

        @Test
        @DisplayName("refuses to move it but still allows changing its type")
        void protectsTicketHistory() {
            book(normalSeat);
            UUID roomId = catalogue.room().getUuid();

            assertThatThrownBy(() -> seatService.update(normalSeat.getUuid(),
                    new SeatRequestDTO(roomId, "Z", "9", SeatType.NORMAL)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE));

            assertThat(seatService.update(normalSeat.getUuid(),
                    new SeatRequestDTO(roomId, "a", " 1 ", SeatType.PREMIUM)).seatType())
                    .isEqualTo(SeatType.PREMIUM);
        }
    }

    @Nested
    @DisplayName("seat holds by other customers")
    class HoldsByOthers {

        @BeforeEach
        void holdNormalSeat() {
            seatHoldService.create(screening.getUuid(), OTHER_CUSTOMER,
                    new SeatHoldRequestDTO(List.of(normalSeat.getUuid())));
        }

        @Test
        @DisplayName("are subtracted from screening availability, except for their owner")
        void reduceAvailability() {
            var anonymous = movieScreeningService.findById(screening.getUuid(), true, null);
            var owner = movieScreeningService.findById(screening.getUuid(), true, OTHER_CUSTOMER);

            assertSoftly(softly -> {
                softly.assertThat(anonymous.totalSeats()).isEqualTo(2);
                softly.assertThat(anonymous.availableSeats()).isEqualTo(1);
                softly.assertThat(anonymous.heldSeats()).isEqualTo(1);
                softly.assertThat(anonymous.soldOut()).isFalse();
                softly.assertThat(owner.availableSeats()).isEqualTo(2);
                softly.assertThat(owner.heldSeats()).isZero();
            });
        }

        @Test
        @DisplayName("make the quote fail just like the purchase would")
        void blockQuotes() {
            assertThatThrownBy(() -> reservationService.quote(CUSTOMER, request(normalSeat)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SEATS_UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("own seat holds")
    class OwnHolds {

        @Test
        @DisplayName("let their owner quote with the hold without consuming it")
        void quoteWithOwnHold() {
            var hold = seatHoldService.create(screening.getUuid(), OTHER_CUSTOMER,
                    new SeatHoldRequestDTO(List.of(normalSeat.getUuid())));

            var quote = reservationService.quote(OTHER_CUSTOMER, new ReservationRequestDTO(
                    screening.getUuid(), List.of(normalSeat.getUuid()), List.of(), hold.uuid()));

            assertThat(quote.totalPrice()).isEqualByComparingTo("10.00");
            assertThat(seatHoldService.findById(hold.uuid(), OTHER_CUSTOMER).uuid()).isEqualTo(hold.uuid());
        }
    }

    @Nested
    @DisplayName("when scheduling a screening")
    class ScheduleScreening {

        @Test
        @DisplayName("refuses to create a screening in the past")
        void refusesPastStart() {
            assertThatThrownBy(() -> movieScreeningService.create(new ScreeningRequestDTO(
                    catalogue.movie().getUuid(), catalogue.room().getUuid(),
                    fixture.now().minusHours(1), new BigDecimal("10.00"))))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @Nested
    @DisplayName("when rescheduling a screening")
    class RescheduleScreening {

        @Test
        @DisplayName("lets a started screening change only its price")
        void editsStartedScreeningPrice() {
            UUID movieId = catalogue.movie().getUuid();
            UUID roomId = catalogue.room().getUuid();
            OffsetDateTime startsAt = storedStart();
            startScreening();

            assertThat(movieScreeningService.update(screening.getUuid(), new ScreeningRequestDTO(
                    movieId, roomId, startsAt, new BigDecimal("8.00"))).basePrice())
                    .isEqualByComparingTo("8.00");

            assertThatThrownBy(() -> movieScreeningService.update(screening.getUuid(), new ScreeningRequestDTO(
                    movieId, roomId, fixture.now().plusDays(3), new BigDecimal("8.00"))))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCREENING_STARTED));
        }

        @Test
        @DisplayName("refuses to move a screening with tickets sold but still allows a price change")
        void protectsSoldScreenings() {
            book(normalSeat);
            UUID movieId = catalogue.movie().getUuid();
            UUID roomId = catalogue.room().getUuid();
            OffsetDateTime startsAt = storedStart();

            assertThatThrownBy(() -> movieScreeningService.update(screening.getUuid(), new ScreeningRequestDTO(
                    movieId, roomId, startsAt.plusHours(5), new BigDecimal("10.00"))))
                    .isInstanceOfSatisfying(ConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE));

            assertThat(movieScreeningService.update(screening.getUuid(), new ScreeningRequestDTO(
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
            book(premiumSeat);

            List<SeatAvailabilityResponseDTO> availability =
                    seatMapService.findSeatMap(screening.getUuid(), null).seats();

            assertThat(availability)
                    .extracting(SeatAvailabilityResponseDTO::seatId, SeatAvailabilityResponseDTO::available)
                    .containsExactlyInAnyOrder(
                            Tuple.tuple(normalSeat.getUuid(), true),
                            Tuple.tuple(premiumSeat.getUuid(), false));
        }
    }
}
