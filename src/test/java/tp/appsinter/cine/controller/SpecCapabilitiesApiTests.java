package tp.appsinter.cine.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.entities.MovieScreening;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.MovieGenre;
import tp.appsinter.cine.model.utils.Position;
import tp.appsinter.cine.model.utils.ProductCategory;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.model.utils.ScreenRoomType;
import tp.appsinter.cine.model.utils.SeatType;
import tp.appsinter.cine.repository.InventoryRepository;
import tp.appsinter.cine.repository.MovieRepository;
import tp.appsinter.cine.repository.MovieScreeningRepository;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.ScreenRoomRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.UserRepository;
import tp.appsinter.cine.service.UserService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de las capacidades incorporadas a partir de {@code docs/backend-spec.md}.
 *
 * <p>Se apoya en los repositorios para armar el catálogo porque lo que importa medir aquí es el
 * comportamiento de los nuevos endpoints, no el alta administrativa que ya cubren otras suites.
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
    private MockMvc mockMvc;
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

    private String bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String reservationBody(UUID... seatIds) {
        String seats = String.join(",", java.util.Arrays.stream(seatIds).map(id -> "\"" + id + "\"").toList());
        return """
                {"movieScreeningId":"%s","seatIds":[%s],"products":[{"inventoryId":"%s","quantity":2}]}
                """.formatted(screening.getUuid(), seats, popcorn.getUuid());
    }

    private String book(String token, UUID... seatIds) throws Exception {
        String body = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(seatIds)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    @Nested
    @DisplayName("public catalogue")
    class PublicCatalogue {

        @Test
        @DisplayName("lists products without authentication")
        void listsProducts() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].productName").value("Popcorn"))
                    .andExpect(jsonPath("$.content[0].price").value(4.00))
                    .andExpect(jsonPath("$.content[0].available").value(true))
                    .andExpect(jsonPath("$.content[0].quantity").doesNotExist());
        }

        @Test
        @DisplayName("filters products by category")
        void filtersByCategory() throws Exception {
            mockMvc.perform(get("/api/products").param("category", "DRINK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("serves enum labels and business config with a cache validator")
        void servesMetadata() throws Exception {
            mockMvc.perform(get("/api/metadata/enums"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.screenRoomType[?(@.value=='THREED')].label").value("3D"))
                    .andExpect(jsonPath("$.screenRoomType[?(@.value=='FOURD')].label").value("4D"))
                    .andExpect(jsonPath("$.movieGenre[?(@.value=='SCI_FI')].label").value("Sci-Fi"));

            mockMvc.perform(get("/api/metadata/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.premiumSeatMultiplier").value(1.50))
                    .andExpect(jsonPath("$.screeningTurnoverMinutes").value(15))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.seatHoldTtlSeconds").isNumber());
        }

        @Test
        @DisplayName("reports availability counts on screenings")
        void reportsAvailability() throws Exception {
            mockMvc.perform(get("/api/screenings/" + screening.getUuid()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSeats").value(2))
                    .andExpect(jsonPath("$.availableSeats").value(2))
                    .andExpect(jsonPath("$.soldOut").value(false));
        }
    }

    @Nested
    @DisplayName("pricing and cancellation")
    class PricingAndCancellation {

        @Test
        @DisplayName("quotes a basket without booking anything")
        void quotesWithoutSideEffects() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);

            mockMvc.perform(post("/api/reservations:quote")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationBody(seatA1.getUuid(), seatA2.getUuid())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticketsTotal").value(25.00))
                    .andExpect(jsonPath("$.itemsTotal").value(8.00))
                    .andExpect(jsonPath("$.totalPrice").value(33.00));

            assertThat(reservationRepository.count()).isZero();
            assertThat(inventoryRepository.findById(popcorn.getUuid()).orElseThrow().getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("charges exactly what the quote announced")
        void quoteMatchesCreate() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);
            String quote = mockMvc.perform(post("/api/reservations/quote")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationBody(seatA1.getUuid(), seatA2.getUuid())))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            mockMvc.perform(post("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationBody(seatA1.getUuid(), seatA2.getUuid())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalPrice")
                            .value(((Number) JsonPath.read(quote, "$.totalPrice")).doubleValue()))
                    .andExpect(jsonPath("$.user.email").value(CUSTOMER_EMAIL))
                    .andExpect(jsonPath("$.roomName").value("Room 1"));
        }

        @Test
        @DisplayName("keeps the cancelled reservation readable and frees the seat")
        void cancelPreservesHistory() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);
            String reservationId = book(token, seatA1.getUuid());

            mockMvc.perform(post("/api/reservations/" + reservationId + "/cancellation")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.totalPrice").value(0))
                    .andExpect(jsonPath("$.originalTotalPrice").value(18.00))
                    .andExpect(jsonPath("$.cancelledAt").exists())
                    .andExpect(jsonPath("$.tickets.length()").value(1))
                    .andExpect(jsonPath("$.items.length()").value(1));

            mockMvc.perform(post("/api/reservations/" + reservationId + "/cancellation")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));

            book(bearer(OTHER_EMAIL), seatA1.getUuid());
        }

        @Test
        @DisplayName("rejects a taken seat with a coded conflict")
        void rejectsTakenSeat() throws Exception {
            book(bearer(CUSTOMER_EMAIL), seatA1.getUuid());

            mockMvc.perform(post("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationBody(seatA1.getUuid())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));
        }
    }

    @Nested
    @DisplayName("seat holds")
    class SeatHolds {

        private String holdBody(UUID... seatIds) {
            String seats = String.join(",", java.util.Arrays.stream(seatIds).map(id -> "\"" + id + "\"").toList());
            return "{\"seatIds\":[%s]}".formatted(seats);
        }

        @Test
        @DisplayName("holds a seat, blocks other buyers and releases it")
        void holdLifecycle() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);
            String created = mockMvc.perform(post("/api/screenings/" + screening.getUuid() + "/holds")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(holdBody(seatA1.getUuid())))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.ttlSeconds").isNumber())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String holdId = JsonPath.read(created, "$.uuid");

            String otherToken = bearer(OTHER_EMAIL);
            mockMvc.perform(post("/api/screenings/" + screening.getUuid() + "/holds")
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(holdBody(seatA1.getUuid())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));

            mockMvc.perform(get("/api/screenings/" + screening.getUuid() + "/seats")
                            .header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seats[?(@.seatId=='%s')].held".formatted(seatA1.getUuid()))
                            .value(true));

            mockMvc.perform(delete("/api/holds/" + holdId).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/holds/" + holdId).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/screenings/" + screening.getUuid() + "/holds")
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(holdBody(seatA1.getUuid())))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("seat layout authoring")
    class SeatLayoutAuthoring {

        @Test
        @DisplayName("creates a whole grid in one call")
        void generatesGrid() throws Exception {
            mockMvc.perform(post("/api/rooms/" + emptyRoom.getUuid() + "/seats:generate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rows":2,"columns":3,"startRow":"A","premiumRows":["B"],"skip":["A2"]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seats.length()").value(5))
                    .andExpect(jsonPath("$.rows").value(org.hamcrest.Matchers.contains("A", "B")));
        }

        @Test
        @DisplayName("rolls the batch back when a position is taken")
        void failsOnConflict() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            String body = """
                    {"seats":[{"row":"A","column":"1","seatType":"NORMAL"},
                              {"row":"A","column":"2","seatType":"NORMAL"}]}
                    """;

            mockMvc.perform(post("/api/rooms/" + emptyRoom.getUuid() + "/seats:bulk")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seats.length()").value(2));

            mockMvc.perform(post("/api/rooms/" + emptyRoom.getUuid() + "/seats/bulk")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NAME_TAKEN"))
                    .andExpect(jsonPath("$.context.conflicts.length()").value(2));

            mockMvc.perform(post("/api/rooms/" + emptyRoom.getUuid() + "/seats/bulk")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"onConflict":"SKIP","seats":[{"row":"A","column":"1","seatType":"NORMAL"},
                                                                  {"row":"A","column":"3","seatType":"PREMIUM"}]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seats.length()").value(3));
        }
    }

    @Nested
    @DisplayName("account management")
    class AccountManagement {

        @Test
        @DisplayName("updates the profile and rotates the password")
        void managesOwnAccount() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);

            mockMvc.perform(patch("/api/users/me")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"Augusta","lastName":"King"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Augusta"));

            mockMvc.perform(post("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"wrongpassword","newPassword":"anothersecret"}
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

            mockMvc.perform(post("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"supersecret","newPassword":"anothersecret"}
                                    """))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ada@example.com","password":"anothersecret"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("promotes a customer but refuses to leave the cinema without admins")
        void managesRoles() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            UUID customerId = userRepository.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow().getUuid();
            UUID adminId = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getUuid();

            mockMvc.perform(patch("/api/users/" + adminId + "/role")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"CUSTOMER"}
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

            mockMvc.perform(patch("/api/users/" + customerId + "/role")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"ADMIN"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("soft-deletes a user and locks them out")
        void softDeletes() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            UUID otherId = userRepository.findByEmailIgnoreCase(OTHER_EMAIL).orElseThrow().getUuid();

            mockMvc.perform(delete("/api/users/" + otherId).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"charles@example.com","password":"supersecret"}
                                    """))
                    .andExpect(status().isUnauthorized());

            assertThat(userRepository.findByEmailIgnoreCase(OTHER_EMAIL)).isPresent();
        }
    }

    @Nested
    @DisplayName("stock adjustments")
    class StockAdjustments {

        @Test
        @DisplayName("applies a delta and records the audit trail")
        void adjustsStock() throws Exception {
            String admin = bearer(ADMIN_EMAIL);

            mockMvc.perform(post("/api/inventory/" + popcorn.getUuid() + "/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"delta":-4,"reason":"spillage"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(6));

            mockMvc.perform(post("/api/inventory/" + popcorn.getUuid() + "/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"delta":-100,"reason":"typo"}
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                    .andExpect(jsonPath("$.context.available").value(6));

            mockMvc.perform(get("/api/inventory/" + popcorn.getUuid() + "/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].delta").value(-4))
                    .andExpect(jsonPath("$.content[0].resultingStock").value(6))
                    .andExpect(jsonPath("$.content[0].adjustedBy").value(ADMIN_EMAIL));
        }
    }

    @Nested
    @DisplayName("reservation listing")
    class ReservationListing {

        @Test
        @DisplayName("filters the admin listing by owner email")
        void filtersByEmail() throws Exception {
            book(bearer(CUSTOMER_EMAIL), seatA1.getUuid());

            mockMvc.perform(get("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .param("email", "ada"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].user.email").value(CUSTOMER_EMAIL));

            mockMvc.perform(get("/api/reservations/all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                            .param("email", "nobody"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("filters my reservations by upcoming screenings")
        void filtersMine() throws Exception {
            String token = bearer(CUSTOMER_EMAIL);
            book(token, seatA1.getUuid());

            mockMvc.perform(get("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .param("upcomingOnly", "true")
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].screeningStartsAt").exists());
        }
    }
}
