package tp.appsinter.cine.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.inventory.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.inventory.dto.request.InventoryItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.shared.dto.request.ArchiveRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.MutableClock;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

@ApiIntegrationTest
@DisplayName("Concessions inventory")
class InventoryApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private MutableClock clock;

    private Catalogue catalogue;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL);
    }

    private MockMvcTester.MockMvcRequestBuilder adjust(int delta, String reason) {
        return mvc.post().uri("/api/inventory/{id}/adjustments", catalogue.popcorn().getUuid())
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new InventoryAdjustmentRequestDTO(delta, reason)));
    }

    private MockMvcTester.MockMvcRequestBuilder createProduct(String name, int quantity) {
        return mvc.post().uri("/api/inventory")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new InventoryItemRequestDTO(name, ProductCategory.DRINK, new BigDecimal("3.50"),
                        quantity)));
    }

    @Nested
    @DisplayName("public menu")
    class PublicMenu {

        @Test
        @DisplayName("lists products without authentication and without the stock")
        void listsProducts() {
            assertThat(mvc.get().uri("/api/products"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[0].productName", name -> assertThat(name).isEqualTo("Popcorn"))
                    .hasPathSatisfying("$.content[0].price",
                            price -> assertThat(price).convertTo(BIG_DECIMAL).isEqualByComparingTo("4.00"))
                    .hasPathSatisfying("$.content[0].available", available -> assertThat(available).isEqualTo(true))
                    .doesNotHavePath("$.content[0].quantity");
        }

        @Test
        @DisplayName("filters products by category")
        void filtersByCategory() {
            assertThat(mvc.get().uri("/api/products").param("category", "SNACK"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].productName").asArray().containsExactly("Popcorn");
            assertThat(mvc.get().uri("/api/products").param("category", "DRINK"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content").asArray().isEmpty();
        }

        @Test
        @DisplayName("takes LIKE wildcards in the search literally")
        void searchEscapesWildcards() {
            assertThat(mvc.get().uri("/api/products").param("search", "_"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/products").param("search", "POP"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("stock history")
    class StockHistory {

        @Test
        @DisplayName("applies a delta and records who did it")
        void adjustsStock() {
            assertThat(adjust(-4, "spillage"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.quantity").isEqualTo(6);

            assertThat(adjust(-100, "typo"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("INSUFFICIENT_STOCK"))
                    .hasPathSatisfying("$.context.available", available -> assertThat(available).isEqualTo(6));

            assertThat(mvc.get().uri("/api/inventory/{id}/adjustments", catalogue.popcorn().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPathSatisfying("$.content[0].delta", delta -> assertThat(delta).isEqualTo(-4))
                    .hasPathSatisfying("$.content[0].resultingStock", stock -> assertThat(stock).isEqualTo(6))
                    .hasPathSatisfying("$.content[0].adjustedBy", by -> assertThat(by).isEqualTo(ADMIN));
        }

        @Test
        @DisplayName("rejects a zero adjustment as a validation error")
        void rejectsZeroDelta() {
            assertThat(adjust(0, "nothing"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("delta"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("NonZero"));
        }

        @Test
        @DisplayName("lists the newest movement first unless asked otherwise")
        void newestFirst() {
            assertThat(adjust(5, "first delivery")).hasStatusOk();
            clock.advance(Duration.ofMinutes(1));
            assertThat(adjust(-1, "breakage")).hasStatusOk();

            assertThat(mvc.get().uri("/api/inventory/{id}/adjustments", catalogue.popcorn().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[*].reason",
                            reasons -> assertThat(reasons).asArray().containsExactly("breakage", "first delivery"))
                    .hasPathSatisfying("$.sort", sort -> assertThat(sort).isEqualTo("createdAt: DESC"));
        }

        @Test
        @DisplayName("records the initial stock of a new product as its first movement")
        void recordsInitialStock() {
            String id = ApiClient.read(createProduct("Water", 24).exchange(), "$.uuid");

            assertThat(mvc.get().uri("/api/inventory/{id}/adjustments", id)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[*].delta", deltas -> assertThat(deltas).asArray().containsExactly(24))
                    .hasPathSatisfying("$.content[0].reason", reason -> assertThat(reason).isEqualTo("Initial stock"))
                    .hasPathSatisfying("$.content[0].adjustedBy", by -> assertThat(by).isEqualTo(ADMIN));
        }
    }

    @Nested
    @DisplayName("archive instead of delete")
    class ArchiveInsteadOfDelete {

        @Test
        @DisplayName("hides an archived product from the menu and refuses to sell it")
        void archivesProduct() {
            assertThat(mvc.patch().uri("/api/inventory/{id}", catalogue.popcorn().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ArchiveRequestDTO(true))))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);

            assertThat(mvc.get().uri("/api/products"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/inventory").header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/inventory").param("includeArchived", "true")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.post().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ReservationRequestDTO(catalogue.screening().getUuid(),
                                    List.of(catalogue.seat(1).getUuid()),
                                    List.of(new ReservationItemRequestDTO(catalogue.popcorn().getUuid(), 1))))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_ARCHIVED"))
                    .hasPathSatisfying("$.context.resource", resource -> assertThat(resource).isEqualTo("Product"));
        }

        @Test
        @DisplayName("deletes a product without history but refuses one with stock movements")
        void deletesOnlyWithoutHistory() {
            assertThat(mvc.delete().uri("/api/inventory/{id}", catalogue.popcorn().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.NO_CONTENT);

            String withStock = ApiClient.read(createProduct("Water", 24).exchange(), "$.uuid");
            assertThat(mvc.delete().uri("/api/inventory/{id}", withStock)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_IN_USE"))
                    .hasPathSatisfying("$.context.referencedBy",
                            by -> assertThat(by).isEqualTo("Inventory adjustment"));
        }

        @Test
        @DisplayName("refuses to delete a product that was sold")
        void refusesSoldProduct() {
            assertThat(mvc.post().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ReservationRequestDTO(catalogue.screening().getUuid(),
                                    List.of(catalogue.seat(1).getUuid()),
                                    List.of(new ReservationItemRequestDTO(catalogue.popcorn().getUuid(), 1))))))
                    .hasStatus(HttpStatus.CREATED);

            assertThat(mvc.delete().uri("/api/inventory/{id}", catalogue.popcorn().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.context.referencedBy").isEqualTo("Reservation item");
        }
    }

    @Nested
    @DisplayName("names")
    class Names {

        @Test
        @DisplayName("treats product names as case-insensitive")
        void caseInsensitiveNames() {
            assertThat(createProduct("POPCORN", 0))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("NAME_TAKEN");
        }

        @Test
        @DisplayName("rejects a price with more than two decimals instead of rounding it")
        void rejectsSubCentPrices() {
            assertThat(mvc.post().uri("/api/inventory")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"productName":"Gum","category":"SNACK","cost":1.005,"quantity":1}
                                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.violations[0].code").isEqualTo("Digits");
        }
    }
}
