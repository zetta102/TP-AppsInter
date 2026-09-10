package tp.appsinter.cine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
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
@DisplayName("Concession purchases")
class ConcessionFlowTests {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
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
    private Seat seat;
    private Inventory popcorn;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        movieScreeningRepository.deleteAll();
        seatRepository.deleteAll();
        screenRoomRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        customer = userRepository.save(User.builder()
                .firstName("Alan")
                .lastName("Turing")
                .email("alan@example.com")
                .password("{noop}secret")
                .role(Role.CUSTOMER)
                .build());

        Movie movie = movieRepository.save(Movie.builder()
                .movieName("Solaris")
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(100)
                .mainCast(new ArrayList<>(List.of("Donatas Banionis")))
                .build());

        ScreenRoom room = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 9")
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());

        seat = seatRepository.save(Seat.builder()
                .position(new Position("A", "1"))
                .seatType(SeatType.NORMAL)
                .screenRoom(room)
                .build());

        screening = movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(OffsetDateTime.now().plusDays(1))
                .endsAt(OffsetDateTime.now().plusDays(1).plusMinutes(115))
                .basePrice(new BigDecimal("10.00"))
                .build());

        popcorn = inventoryRepository.save(Inventory.builder()
                .productName("Popcorn")
                .category(ProductCategory.SNACK)
                .cost(new BigDecimal("4.50"))
                .quantity(10)
                .build());
    }

    private CreateReservationResponseDTO book(ReservationItemRequestDTO... products) {
        return reservationService.createReservation(customer.getEmail(),
                new CreateReservationDTO(screening.getUuid(), List.of(seat.getUuid()), List.of(products)));
    }

    @Nested
    @DisplayName("when products are added to a reservation")
    class WithProducts {

        @Test
        @DisplayName("adds line items, draws down stock and rolls the cost into the total")
        void addsLineItems() {
            CreateReservationResponseDTO response = book(new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertAll(
                    () -> assertThat(response.items()).hasSize(1),
                    () -> assertThat(response.items().getFirst().productName()).isEqualTo("Popcorn"),
                    () -> assertThat(response.items().getFirst().lineTotal())
                            .isEqualByComparingTo(new BigDecimal("9.00")),
                    // 10.00 ticket + 9.00 popcorn
                    () -> assertThat(response.totalPrice()).isEqualByComparingTo(new BigDecimal("19.00")),
                    () -> assertThat(inventoryRepository.findById(popcorn.getUuid()))
                            .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(8)));
        }

        @Test
        @DisplayName("merges a repeated product into a single line")
        void mergesRepeatedProduct() {
            CreateReservationResponseDTO response = book(
                    new ReservationItemRequestDTO(popcorn.getUuid(), 1),
                    new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertAll(
                    () -> assertThat(response.items()).hasSize(1),
                    () -> assertThat(response.items().getFirst().quantity()).isEqualTo(3),
                    () -> assertThat(inventoryRepository.findById(popcorn.getUuid()))
                            .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(7)));
        }

        @Test
        @DisplayName("rejects an order larger than the stock on hand")
        void rejectsInsufficientStock() {
            assertThatThrownBy(() -> book(new ReservationItemRequestDTO(popcorn.getUuid(), 11)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("in stock");

            assertThat(inventoryRepository.findById(popcorn.getUuid()))
                    .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(10));
        }

        @Test
        @DisplayName("rejects an unknown product")
        void rejectsUnknownProduct() {
            assertThatThrownBy(() -> book(new ReservationItemRequestDTO(UUID.randomUUID(), 1)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("when the reservation is cancelled")
    class OnCancellation {

        @Test
        @DisplayName("puts the concessions back on the shelf")
        void restoresStock() {
            CreateReservationResponseDTO created = book(new ReservationItemRequestDTO(popcorn.getUuid(), 3));
            assertThat(inventoryRepository.findById(popcorn.getUuid()))
                    .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(7));

            CreateReservationResponseDTO cancelled =
                    reservationService.cancelReservation(created.uuid(), customer.getEmail(), false);

            assertAll(
                    // La cancelación conserva el historial: las líneas siguen ahí y el total pasa a
                    // cero, con el importe original guardado aparte.
                    () -> assertThat(cancelled.items()).hasSize(1),
                    () -> assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED),
                    () -> assertThat(cancelled.cancelledAt()).isNotNull(),
                    () -> assertThat(cancelled.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(cancelled.originalTotalPrice()).isGreaterThan(BigDecimal.ZERO),
                    () -> assertThat(inventoryRepository.findById(popcorn.getUuid()))
                            .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(10)));
        }
    }
}
