package tp.appsinter.cine.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.movie.Movie;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.movie.MovieRepository;
import tp.appsinter.cine.reservation.ReservationService;
import tp.appsinter.cine.reservation.ReservationStatus;
import tp.appsinter.cine.reservation.dto.CreateReservationRequestDTO;
import tp.appsinter.cine.reservation.dto.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.ReservationResponseDTO;
import tp.appsinter.cine.room.Position;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomRepository;
import tp.appsinter.cine.room.ScreenRoomType;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Concession purchases")
class ConcessionFlowTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    /**
     * Espía que llama al repositorio real; sólo la prueba de concurrencia le cambia el comportamiento.
     */
    @MockitoSpyBean
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
        DatabaseCleaner.truncate(jdbcTemplate);

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

    private ReservationResponseDTO book(ReservationItemRequestDTO... products) {
        return reservationService.createReservation(customer.getEmail(),
                new CreateReservationRequestDTO(screening.getUuid(), List.of(seat.getUuid()), List.of(products)));
    }

    @Nested
    @DisplayName("when products are added to a reservation")
    class WithProducts {

        @Test
        @DisplayName("adds line items, draws down stock and rolls the cost into the total")
        void addsLineItems() {
            ReservationResponseDTO response = book(new ReservationItemRequestDTO(popcorn.getUuid(), 2));

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
            ReservationResponseDTO response = book(
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
            ReservationResponseDTO created = book(new ReservationItemRequestDTO(popcorn.getUuid(), 3));
            assertThat(inventoryRepository.findById(popcorn.getUuid()))
                    .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(7));

            ReservationResponseDTO cancelled =
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

    @Nested
    @DisplayName("when another write changes the stock concurrently")
    class OnConcurrentStockChange {

        @Test
        @DisplayName("retries the purchase against the fresh stock instead of failing")
        void retriesOnVersionConflict() {
            TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);
            otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            AtomicBoolean interfered = new AtomicBoolean();
            // Justo después de que la compra lee el producto, otra transacción confirma un cambio sobre
            // él: la versión que la compra tiene en memoria queda vieja y su escritura pierde.
            doAnswer(invocation -> {
                // El espía de un repositorio delega en el proxy de Spring Data y no tiene «método real»
                // al que llamar; findAllById hace la misma lectura, porque uuid es el identificador.
                List<UUID> ids = invocation.getArgument(0);
                List<Inventory> loaded = inventoryRepository.findAllById(ids);
                if (interfered.compareAndSet(false, true)) {
                    otherTransaction.executeWithoutResult(
                            status -> inventoryRepository.applyDelta(popcorn.getUuid(), 0));
                }
                return loaded;
            }).when(inventoryRepository).findByUuidIn(anyList());

            ReservationResponseDTO response = book(new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertAll(
                    () -> assertThat(response.items()).hasSize(1),
                    () -> assertThat(inventoryRepository.findById(popcorn.getUuid()))
                            .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(8)),
                    // Un intento perdió contra la versión nueva y el reintento la leyó fresca.
                    () -> verify(inventoryRepository, times(2)).findByUuidIn(anyList()));
        }
    }
}
