package tp.appsinter.cine.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.reservation.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.OTHER_CUSTOMER;

/**
 * Presupuesto, compra, cancelación y listados de reservas.
 *
 * <p>Los importes se comparan como {@link BigDecimal}: el JSON no distingue {@code 0} de
 * {@code 0.00}, y la escala no es parte del contrato.
 */
@ApiIntegrationTest
@DisplayName("Reservations")
class ReservationApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private InventoryRepository inventoryRepository;

    private Catalogue catalogue;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL, SeatType.PREMIUM);
    }

    /**
     * Las butacas pedidas con dos pochoclos.
     */
    private String basket(Seat... seats) {
        return api.json(new ReservationRequestDTO(catalogue.screening().getUuid(),
                Arrays.stream(seats).map(Seat::getUuid).toList(),
                List.of(new ReservationItemRequestDTO(catalogue.popcorn().getUuid(), 2))));
    }

    private MockMvcTester.MockMvcRequestBuilder reserve(String email, Seat... seats) {
        return mvc.post().uri("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(basket(seats));
    }

    private MockMvcTester.MockMvcRequestBuilder quote(String path, Seat... seats) {
        return mvc.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(basket(seats));
    }

    private String book(String email, Seat... seats) {
        MvcTestResult booked = reserve(email, seats).exchange();
        assertThat(booked).hasStatus(HttpStatus.CREATED);
        return ApiClient.read(booked, "$.uuid");
    }

    @Nested
    @DisplayName("quote and purchase")
    class QuoteAndPurchase {

        @Test
        @DisplayName("quotes a basket without booking anything")
        void quotesWithoutSideEffects() {
            assertThat(quote("/api/reservations:quote", catalogue.seat(1), catalogue.seat(2)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.ticketsTotal",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("25.00"))
                    .hasPathSatisfying("$.itemsTotal",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("8.00"))
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("33.00"));

            assertThat(reservationRepository.count()).isZero();
            assertThat(inventoryRepository.findById(catalogue.popcorn().getUuid()).orElseThrow().getQuantity())
                    .isEqualTo(CatalogueFixture.POPCORN_STOCK);
        }

        @Test
        @DisplayName("answers the quote on the path without the colon too")
        void quoteAlias() {
            assertThat(quote("/api/reservations/quote", catalogue.seat(1)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.ticketsTotal").convertTo(BIG_DECIMAL).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("charges exactly what the quote announced")
        void quoteMatchesCreate() {
            MvcTestResult quote = quote("/api/reservations:quote", catalogue.seat(1), catalogue.seat(2)).exchange();
            assertThat(quote).hasStatusOk();
            BigDecimal quoted = assertThat(quote).bodyJson().extractingPath("$.totalPrice")
                    .convertTo(BIG_DECIMAL).actual();

            assertThat(reserve(CUSTOMER, catalogue.seat(1), catalogue.seat(2)))
                    .hasStatus(HttpStatus.CREATED)
                    .containsHeader(HttpHeaders.LOCATION)
                    .bodyJson()
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo(quoted))
                    .hasPathSatisfying("$.user.email", email -> assertThat(email).isEqualTo(CUSTOMER))
                    .hasPathSatisfying("$.roomName", room -> assertThat(room).isEqualTo("Room 1"));
        }

        @Test
        @DisplayName("rejects a taken seat with a coded conflict")
        void rejectsTakenSeat() {
            book(CUSTOMER, catalogue.seat(1));

            assertThat(reserve(OTHER_CUSTOMER, catalogue.seat(1)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("SEATS_UNAVAILABLE"))
                    .hasPathSatisfying("$.context.seatIds",
                            ids -> assertThat(ids).asArray().containsExactly(catalogue.seat(1).getUuid().toString()));
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("keeps the cancelled reservation readable, marks its lines and frees the seat")
        void cancelPreservesHistory() {
            String reservationId = book(CUSTOMER, catalogue.seat(1));

            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("CANCELLED"))
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("0"))
                    .hasPathSatisfying("$.originalTotalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("18.00"))
                    .hasPath("$.cancelledAt")
                    .hasPathSatisfying("$.tickets[*].cancelledAt", at -> assertThat(at).asArray().hasSize(1)
                            .doesNotContainNull())
                    .hasPathSatisfying("$.items[*].cancelledAt", at -> assertThat(at).asArray().hasSize(1)
                            .doesNotContainNull());

            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("ALREADY_CANCELLED");

            assertThat(reserve(OTHER_CUSTOMER, catalogue.seat(1)))
                    .as("the freed seat can be sold again")
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("hides someone else's reservation behind a 404")
        void hidesForeignReservation() {
            String reservationId = book(CUSTOMER, catalogue.seat(1));

            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(OTHER_CUSTOMER)))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/api/reservations/{id}", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk();
        }
    }

    @Nested
    @DisplayName("listings")
    class Listings {

        @Test
        @DisplayName("filters the admin listing by owner email, taking wildcards literally")
        void filtersByEmail() {
            book(CUSTOMER, catalogue.seat(1));

            assertThat(mvc.get().uri("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .param("email", "ada"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPathSatisfying("$.content[0].user.email", email -> assertThat(email).isEqualTo(CUSTOMER));

            assertThat(mvc.get().uri("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .param("email", "_"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content").asArray().isEmpty();
        }

        @Test
        @DisplayName("filters my reservations by upcoming screenings")
        void filtersMine() {
            book(CUSTOMER, catalogue.seat(1));

            assertThat(mvc.get().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .param("upcomingOnly", "true")
                            .param("status", "CONFIRMED"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPath("$.content[0].screeningStartsAt");
        }

        @Test
        @DisplayName("reports the order with the published property names")
        void echoesPublishedSort() {
            book(CUSTOMER, catalogue.seat(1));

            assertThat(mvc.get().uri("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .param("sort", "userEmail,desc"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.sort").isEqualTo("userEmail: DESC");
        }

        @Test
        @DisplayName("does not sort my reservations by owner, since they are all mine")
        void ownListingHasNoOwnerSort() {
            assertThat(mvc.get().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .param("sort", "userEmail"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT_PROPERTY");
        }
    }
}
