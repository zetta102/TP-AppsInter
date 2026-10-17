package tp.appsinter.cine.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.inventory.ProductCategory;
import tp.appsinter.cine.movie.Movie;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.movie.MovieRepository;
import tp.appsinter.cine.reservation.ReservationRepository;
import tp.appsinter.cine.room.Position;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomRepository;
import tp.appsinter.cine.room.ScreenRoomType;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.CreateUserRequestDTO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

/**
 * Cobertura de las capacidades incorporadas a partir de {@code docs/backend-spec.md}.
 *
 * <p>Se apoya en los repositorios para armar el catálogo porque lo que importa medir aquí es el
 * comportamiento de los nuevos endpoints, no el alta administrativa que ya cubren otras suites.
 *
 * <p>Los importes se comparan como {@link BigDecimal}: el JSON no distingue {@code 0} de
 * {@code 0.00}, y la escala no es parte del contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Backend specification capabilities")
class SpecCapabilitiesApiTests {

    private static final String CUSTOMER_EMAIL = "ada@example.com";
    private static final String OTHER_EMAIL = "charles@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "supersecret";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private MovieScreeningRepository movieScreeningRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ScreenRoomRepository screenRoomRepository;
    @Autowired
    private MovieRepository movieRepository;

    private MovieScreening screening;
    private ScreenRoom emptyRoom;
    private Seat seatA1;
    private Seat seatA2;
    private Inventory popcorn;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncate(jdbcTemplate);

        userService.createUser(new CreateUserRequestDTO("Ada", "Lovelace", CUSTOMER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new CreateUserRequestDTO("Charles", "Babbage", OTHER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new CreateUserRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);

        Movie movie = movieRepository.save(Movie.builder()
                .movieName("Arrival")
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(116)
                .mainCast(new ArrayList<>(List.of("Amy Adams")))
                .build());

        ScreenRoom room = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 1")
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());
        emptyRoom = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 2")
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());

        seatA1 = seatRepository.save(Seat.builder()
                .position(new Position("A", "1"))
                .seatType(SeatType.NORMAL)
                .screenRoom(room)
                .build());
        seatA2 = seatRepository.save(Seat.builder()
                .position(new Position("A", "2"))
                .seatType(SeatType.PREMIUM)
                .screenRoom(room)
                .build());

        screening = movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(OffsetDateTime.now().plusDays(2))
                .endsAt(OffsetDateTime.now().plusDays(2).plusMinutes(116))
                .basePrice(new BigDecimal("10.00"))
                .build());

        popcorn = inventoryRepository.save(Inventory.builder()
                .productName("Popcorn")
                .category(ProductCategory.SNACK)
                .cost(new BigDecimal("4.00"))
                .quantity(10)
                .build());
    }

    private static String read(MvcTestResult result, String path) {
        return assertThat(result).bodyJson().extractingPath(path).asString().actual();
    }

    private String bearer(String email) {
        MvcTestResult login = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();
        assertThat(login).hasStatusOk();
        return "Bearer " + read(login, "$.token");
    }

    private static String quoted(UUID... ids) {
        return String.join(",", Arrays.stream(ids).map(id -> "\"" + id + "\"").toList());
    }

    private String reservationBody(UUID... seatIds) {
        return """
                {"movieScreeningId":"%s","seatIds":[%s],"products":[{"inventoryId":"%s","quantity":2}]}
                """.formatted(screening.getUuid(), quoted(seatIds), popcorn.getUuid());
    }

    private MockMvcTester.MockMvcRequestBuilder reserve(String token, UUID... seatIds) {
        return mvc.post().uri("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(seatIds));
    }

    private String book(String token, UUID... seatIds) {
        MvcTestResult booked = reserve(token, seatIds).exchange();
        assertThat(booked).hasStatus(HttpStatus.CREATED);
        return read(booked, "$.uuid");
    }

    @Nested
    @DisplayName("public catalogue")
    class PublicCatalogue {

        @Test
        @DisplayName("lists products without authentication")
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
            assertThat(mvc.get().uri("/api/products").param("category", "DRINK"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content").isEmpty();
        }

        @Test
        @DisplayName("serves enum labels and business config with a cache validator")
        void servesMetadata() {
            assertThat(mvc.get().uri("/api/metadata/enums"))
                    .hasStatusOk()
                    .containsHeader(HttpHeaders.ETAG)
                    .bodyJson()
                    .hasPathSatisfying("$.screenRoomType[?(@.value=='THREED')].label",
                            label -> assertThat(label).asArray().containsExactly("3D"))
                    .hasPathSatisfying("$.screenRoomType[?(@.value=='FOURD')].label",
                            label -> assertThat(label).asArray().containsExactly("4D"))
                    .hasPathSatisfying("$.movieGenre[?(@.value=='SCI_FI')].label",
                            label -> assertThat(label).asArray().containsExactly("Sci-Fi"));

            assertThat(mvc.get().uri("/api/metadata/config"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.premiumSeatMultiplier",
                            multiplier -> assertThat(multiplier).convertTo(BIG_DECIMAL).isEqualByComparingTo("1.50"))
                    .hasPathSatisfying("$.screeningTurnoverMinutes", minutes -> assertThat(minutes).isEqualTo(15))
                    .hasPathSatisfying("$.currency", currency -> assertThat(currency).isEqualTo("USD"))
                    .hasPathSatisfying("$.seatHoldTtlSeconds", ttl -> assertThat(ttl).asNumber());
        }

        @Test
        @DisplayName("lists screenings with and without date bounds")
        void listsScreenings() {
            // Regresión: sin `from`/`to`, PostgreSQL no podía tipar el parámetro nulo y devolvía 500.
            assertThat(mvc.get().uri("/api/screenings"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.get().uri("/api/screenings").param("upcomingOnly", "true"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[0].uuid").isEqualTo(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings")
                            .param("from", OffsetDateTime.now().plusDays(3).toString())
                            .param("to", OffsetDateTime.now().plusDays(10).toString()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/screenings").param("movieId", screening.getMovie().getUuid().toString()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("reports availability counts on screenings")
        void reportsAvailability() {
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalSeats", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.availableSeats", available -> assertThat(available).isEqualTo(2))
                    .hasPathSatisfying("$.soldOut", soldOut -> assertThat(soldOut).isEqualTo(false));
        }
    }

    @Nested
    @DisplayName("pricing and cancellation")
    class PricingAndCancellation {

        @Test
        @DisplayName("quotes a basket without booking anything")
        void quotesWithoutSideEffects() {
            String token = bearer(CUSTOMER_EMAIL);

            assertThat(mvc.post().uri("/api/reservations:quote")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationBody(seatA1.getUuid(), seatA2.getUuid())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.ticketsTotal",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("25.00"))
                    .hasPathSatisfying("$.itemsTotal",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("8.00"))
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("33.00"));

            assertThat(reservationRepository.count()).isZero();
            assertThat(inventoryRepository.findById(popcorn.getUuid()).orElseThrow().getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("charges exactly what the quote announced")
        void quoteMatchesCreate() {
            String token = bearer(CUSTOMER_EMAIL);
            MvcTestResult quote = mvc.post().uri("/api/reservations/quote")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reservationBody(seatA1.getUuid(), seatA2.getUuid()))
                    .exchange();
            assertThat(quote).hasStatusOk();
            BigDecimal quoted = assertThat(quote).bodyJson().extractingPath("$.totalPrice")
                    .convertTo(BIG_DECIMAL).actual();

            assertThat(reserve(token, seatA1.getUuid(), seatA2.getUuid()))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo(quoted))
                    .hasPathSatisfying("$.user.email", email -> assertThat(email).isEqualTo(CUSTOMER_EMAIL))
                    .hasPathSatisfying("$.roomName", room -> assertThat(room).isEqualTo("Room 1"));
        }

        @Test
        @DisplayName("keeps the cancelled reservation readable and frees the seat")
        void cancelPreservesHistory() {
            String token = bearer(CUSTOMER_EMAIL);
            String reservationId = book(token, seatA1.getUuid());

            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("CANCELLED"))
                    .hasPathSatisfying("$.totalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("0"))
                    .hasPathSatisfying("$.originalTotalPrice",
                            total -> assertThat(total).convertTo(BIG_DECIMAL).isEqualByComparingTo("18.00"))
                    .hasPath("$.cancelledAt")
                    .hasPathSatisfying("$.tickets", tickets -> assertThat(tickets).asArray().hasSize(1))
                    .hasPathSatisfying("$.items", items -> assertThat(items).asArray().hasSize(1));

            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservationId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("ALREADY_CANCELLED");

            book(bearer(OTHER_EMAIL), seatA1.getUuid());
        }

        @Test
        @DisplayName("rejects a taken seat with a coded conflict")
        void rejectsTakenSeat() {
            book(bearer(CUSTOMER_EMAIL), seatA1.getUuid());

            assertThat(reserve(bearer(OTHER_EMAIL), seatA1.getUuid()))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("seat holds")
    class SeatHolds {

        private MockMvcTester.MockMvcRequestBuilder hold(String token, UUID... seatIds) {
            return mvc.post().uri("/api/screenings/{id}/holds", screening.getUuid())
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seatIds\":[%s]}".formatted(quoted(seatIds)));
        }

        @Test
        @DisplayName("holds a seat, blocks other buyers and releases it")
        void holdLifecycle() {
            String token = bearer(CUSTOMER_EMAIL);
            MvcTestResult created = hold(token, seatA1.getUuid()).exchange();
            assertThat(created)
                    .hasStatus(HttpStatus.CREATED)
                    .containsHeader(HttpHeaders.LOCATION)
                    .bodyJson()
                    .hasPath("$.expiresAt")
                    .hasPathSatisfying("$.ttlSeconds", ttl -> assertThat(ttl).asNumber());
            String holdId = read(created, "$.uuid");

            String otherToken = bearer(OTHER_EMAIL);
            assertThat(hold(otherToken, seatA1.getUuid()))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");

            assertThat(mvc.get().uri("/api/screenings/{id}/seats", screening.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, otherToken))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.seats[?(@.seatId=='%s')].held".formatted(seatA1.getUuid()))
                    .asArray().containsExactly(true);

            assertThat(mvc.delete().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, token))
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(mvc.delete().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, token))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(hold(otherToken, seatA1.getUuid())).hasStatus(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("seat layout authoring")
    class SeatLayoutAuthoring {

        @Test
        @DisplayName("creates a whole grid in one call")
        void generatesGrid() {
            assertThat(mvc.post().uri("/api/rooms/{id}/seats:generate", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rows":2,"columns":3,"startRow":"A","premiumRows":["B"],"skip":["A2"]}
                                    """))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPathSatisfying("$.seats", seats -> assertThat(seats).asArray().hasSize(5))
                    .hasPathSatisfying("$.rows", rows -> assertThat(rows).asArray().containsExactly("A", "B"));
        }

        @Test
        @DisplayName("rolls the batch back when a position is taken")
        void failsOnConflict() {
            String admin = bearer(ADMIN_EMAIL);
            String body = """
                    {"seats":[{"row":"A","column":"1","seatType":"NORMAL"},
                              {"row":"A","column":"2","seatType":"NORMAL"}]}
                    """;

            assertThat(mvc.post().uri("/api/rooms/{id}/seats:bulk", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.seats").asArray().hasSize(2);

            assertThat(mvc.post().uri("/api/rooms/{id}/seats/bulk", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("NAME_TAKEN"))
                    .hasPathSatisfying("$.context.conflicts",
                            conflicts -> assertThat(conflicts).asArray().hasSize(2));

            assertThat(mvc.post().uri("/api/rooms/{id}/seats/bulk", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"onConflict":"SKIP","seats":[{"row":"A","column":"1","seatType":"NORMAL"},
                                                                  {"row":"A","column":"3","seatType":"PREMIUM"}]}
                                    """))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.seats").asArray().hasSize(3);
        }
    }

    @Nested
    @DisplayName("account management")
    class AccountManagement {

        @Test
        @DisplayName("updates the profile and rotates the password")
        void managesOwnAccount() {
            String token = bearer(CUSTOMER_EMAIL);

            assertThat(mvc.patch().uri("/api/users/me")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"Augusta","lastName":"King"}
                                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.firstName").isEqualTo("Augusta");

            assertThat(mvc.post().uri("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"wrongpassword","newPassword":"anothersecret"}
                                    """))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("WRONG_PASSWORD");

            assertThat(mvc.post().uri("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"supersecret","newPassword":"anothersecret"}
                                    """))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(mvc.post().uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ada@example.com","password":"anothersecret"}
                                    """))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("promotes a customer but refuses to leave the cinema without admins")
        void managesRoles() {
            String admin = bearer(ADMIN_EMAIL);
            UUID customerId = userRepository.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow().getUuid();
            UUID adminId = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getUuid();

            assertThat(mvc.patch().uri("/api/users/{id}/role", adminId)
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"CUSTOMER"}
                                    """))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN");

            assertThat(mvc.patch().uri("/api/users/{id}/role", customerId)
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"ADMIN"}
                                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("soft-deletes a user and locks them out")
        void softDeletes() {
            String admin = bearer(ADMIN_EMAIL);
            UUID otherId = userRepository.findByEmailIgnoreCase(OTHER_EMAIL).orElseThrow().getUuid();

            assertThat(mvc.delete().uri("/api/users/{id}", otherId).header(HttpHeaders.AUTHORIZATION, admin))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(mvc.post().uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"charles@example.com","password":"supersecret"}
                                    """))
                    .hasStatus(HttpStatus.UNAUTHORIZED);

            assertThat(userRepository.findByEmailIgnoreCase(OTHER_EMAIL)).isPresent();
        }
    }

    @Nested
    @DisplayName("stock adjustments")
    class StockAdjustments {

        @Test
        @DisplayName("applies a delta and records the audit trail")
        void adjustsStock() {
            String admin = bearer(ADMIN_EMAIL);

            assertThat(mvc.post().uri("/api/inventory/{id}/adjustments", popcorn.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"delta":-4,"reason":"spillage"}
                                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.quantity").isEqualTo(6);

            assertThat(mvc.post().uri("/api/inventory/{id}/adjustments", popcorn.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"delta":-100,"reason":"typo"}
                                    """))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("INSUFFICIENT_STOCK"))
                    .hasPathSatisfying("$.context.available", available -> assertThat(available).isEqualTo(6));

            assertThat(mvc.get().uri("/api/inventory/{id}/adjustments", popcorn.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPathSatisfying("$.content[0].delta", delta -> assertThat(delta).isEqualTo(-4))
                    .hasPathSatisfying("$.content[0].resultingStock", stock -> assertThat(stock).isEqualTo(6))
                    .hasPathSatisfying("$.content[0].adjustedBy", by -> assertThat(by).isEqualTo(ADMIN_EMAIL));
        }
    }

    @Nested
    @DisplayName("reservation listing")
    class ReservationListing {

        @Test
        @DisplayName("filters the admin listing by owner email")
        void filtersByEmail() {
            book(bearer(CUSTOMER_EMAIL), seatA1.getUuid());

            assertThat(mvc.get().uri("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .param("email", "ada"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPathSatisfying("$.content[0].user.email", email -> assertThat(email).isEqualTo(CUSTOMER_EMAIL));

            assertThat(mvc.get().uri("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .param("email", "nobody"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content").isEmpty();
        }

        @Test
        @DisplayName("filters my reservations by upcoming screenings")
        void filtersMine() {
            String token = bearer(CUSTOMER_EMAIL);
            book(token, seatA1.getUuid());

            assertThat(mvc.get().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .param("upcomingOnly", "true")
                            .param("status", "CONFIRMED"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().hasSize(1))
                    .hasPath("$.content[0].screeningStartsAt");
        }
    }
}
