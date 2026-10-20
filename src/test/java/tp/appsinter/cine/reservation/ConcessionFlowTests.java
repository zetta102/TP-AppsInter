package tp.appsinter.cine.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.reservation.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.reservation.dto.response.ReservationResponseDTO;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tp.appsinter.cine.support.CatalogueFixture.POPCORN_STOCK;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

/**
 * Confitería dentro de una compra, a nivel de servicio.
 *
 * <p>El {@code @MockitoSpyBean} cambia la definición de un bean, así que esta clase no comparte el
 * contexto de Spring con el resto de las {@link ApiIntegrationTest}: arranca uno propio. Se acepta
 * por la prueba de concurrencia, que no tiene otra forma de meter una escritura entre la lectura y
 * la escritura de la compra.
 */
@ApiIntegrationTest
@DisplayName("Concession purchases")
class ConcessionFlowTests {

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
    private UserService userService;
    @Autowired
    private CatalogueFixture fixture;

    private Catalogue catalogue;
    private Inventory popcorn;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL);
        popcorn = catalogue.popcorn();
    }

    private ReservationResponseDTO book(ReservationItemRequestDTO... products) {
        return reservationService.createReservation(CUSTOMER, new ReservationRequestDTO(
                catalogue.screening().getUuid(), List.of(catalogue.seat(1).getUuid()), List.of(products)));
    }

    private int stock() {
        return inventoryRepository.findById(popcorn.getUuid()).orElseThrow().getQuantity();
    }

    @Nested
    @DisplayName("when products are added to a reservation")
    class WithProducts {

        @Test
        @DisplayName("adds line items, draws down stock and rolls the cost into the total")
        void addsLineItems() {
            ReservationResponseDTO response = book(new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertSoftly(softly -> {
                softly.assertThat(response.items()).hasSize(1);
                softly.assertThat(response.items().getFirst().productName()).isEqualTo("Popcorn");
                softly.assertThat(response.items().getFirst().lineTotal()).isEqualByComparingTo("8.00");
                // 10.00 la entrada + 8.00 los pochoclos
                softly.assertThat(response.totalPrice()).isEqualByComparingTo("18.00");
                softly.assertThat(stock()).isEqualTo(POPCORN_STOCK - 2);
            });
        }

        @Test
        @DisplayName("merges a repeated product into a single line")
        void mergesRepeatedProduct() {
            ReservationResponseDTO response = book(
                    new ReservationItemRequestDTO(popcorn.getUuid(), 1),
                    new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertSoftly(softly -> {
                softly.assertThat(response.items()).hasSize(1);
                softly.assertThat(response.items().getFirst().quantity()).isEqualTo(3);
                softly.assertThat(stock()).isEqualTo(POPCORN_STOCK - 3);
            });
        }

        @Test
        @DisplayName("rejects an order larger than the stock on hand")
        void rejectsInsufficientStock() {
            assertThatThrownBy(() -> book(new ReservationItemRequestDTO(popcorn.getUuid(), POPCORN_STOCK + 1)))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

            assertThat(stock()).isEqualTo(POPCORN_STOCK);
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
        @DisplayName("puts the concessions back on the shelf and marks the line")
        void restoresStock() {
            ReservationResponseDTO created = book(new ReservationItemRequestDTO(popcorn.getUuid(), 3));
            assertThat(stock()).isEqualTo(POPCORN_STOCK - 3);

            ReservationResponseDTO cancelled = reservationService.cancelReservation(created.uuid(), CUSTOMER, false);

            assertSoftly(softly -> {
                // La cancelación conserva el historial: las líneas siguen ahí y el total pasa a cero,
                // con el importe original guardado aparte.
                softly.assertThat(cancelled.items()).hasSize(1);
                softly.assertThat(cancelled.items().getFirst().cancelledAt()).isNotNull();
                softly.assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
                softly.assertThat(cancelled.cancelledAt()).isNotNull();
                softly.assertThat(cancelled.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
                softly.assertThat(cancelled.originalTotalPrice()).isEqualByComparingTo("22.00");
                softly.assertThat(stock()).isEqualTo(POPCORN_STOCK);
            });
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
                Collection<UUID> ids = invocation.getArgument(0);
                List<Inventory> loaded = inventoryRepository.findAllById(ids);
                if (interfered.compareAndSet(false, true)) {
                    otherTransaction.executeWithoutResult(
                            _ -> inventoryRepository.applyDelta(popcorn.getUuid(), 0));
                }
                return loaded;
            }).when(inventoryRepository).findByUuidIn(anyCollection());

            ReservationResponseDTO response = book(new ReservationItemRequestDTO(popcorn.getUuid(), 2));

            assertThat(response.items()).hasSize(1);
            assertThat(stock()).isEqualTo(POPCORN_STOCK - 2);
            // Un intento perdió contra la versión nueva y el reintento la leyó fresca.
            verify(inventoryRepository, times(2)).findByUuidIn(anyCollection());
        }
    }
}
