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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
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
import tp.appsinter.cine.repository.ScreenRoomRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.service.SeatStreamService;
import tp.appsinter.cine.service.UserService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de los pedidos del frontend ({@code docs/frontend-status-for-backend.md} §8), cada uno
 * con la prueba de aceptación que propuso el propio documento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Frontend requests")
class FrontendRequestsApiTests {

    private static final String CUSTOMER_EMAIL = "ada@example.com";
    private static final String OTHER_EMAIL = "charles@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "supersecret";
    private static final String PROBLEMS = "https://cine.example/problems/";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ScreenRoomRepository screenRoomRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private MovieScreeningRepository movieScreeningRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private SeatStreamService seatStreamService;

    private Movie movie;
    private ScreenRoom room;
    private MovieScreening screening;
    private Seat seatA1;
    private Seat seatA2;
    private Seat seatA3;
    private Inventory popcorn;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncate(jdbcTemplate);
        userService.createUser(new CreateUserRequestDTO("Ada", "Lovelace", CUSTOMER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new CreateUserRequestDTO("Charles", "Babbage", OTHER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new CreateUserRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);

        movie = movieRepository.save(Movie.builder()
                .movieName("Arrival")
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(116)
                .mainCast(new ArrayList<>(List.of("Amy Adams")))
                .build());
        room = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 1")
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());
        seatA1 = seat("1");
        seatA2 = seat("2");
        seatA3 = seat("3");
        screening = screeningAt(OffsetDateTime.now().plusDays(2));
        popcorn = inventoryRepository.save(Inventory.builder()
                .productName("Popcorn")
                .category(ProductCategory.SNACK)
                .cost(new BigDecimal("4.00"))
                .quantity(10)
                .build());
    }

    private Seat seat(String column) {
        return seatRepository.save(Seat.builder()
                .position(new Position("A", column))
                .seatType(SeatType.NORMAL)
                .screenRoom(room)
                .build());
    }

    private MovieScreening screeningAt(OffsetDateTime startsAt) {
        return movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(131))
                .basePrice(new BigDecimal("10.00"))
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

    private static String seatIds(Seat... seats) {
        return "{\"seatIds\":[%s]}".formatted(String.join(",",
                Arrays.stream(seats).map(seat -> "\"" + seat.getUuid() + "\"").toList()));
    }

    private ResultActions hold(String token, MovieScreening target, Seat... seats) throws Exception {
        return mockMvc.perform(post("/api/screenings/{id}/holds", target.getUuid())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatIds(seats)));
    }

    private String book(String token, Seat... seats) throws Exception {
        String body = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieScreeningId":"%s","seatIds":%s}
                                """.formatted(screening.getUuid(),
                                seatIds(seats).replace("{\"seatIds\":", "").replace("]}", "]"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private ResultActions archive(String token, String resource, UUID id, boolean archived) throws Exception {
        return mockMvc.perform(patch("/api/{resource}/{id}", resource, id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":%s}".formatted(archived)));
    }

    @Nested
    @DisplayName("B2 movie URL validation")
    class UrlValidation {

        private ResultActions createMovie(String posterUrl) throws Exception {
            return mockMvc.perform(post("/api/movies")
                    .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"movieName":"Dune","genres":["SCI_FI"],"runtime":155,"posterUrl":"%s"}
                            """.formatted(posterUrl)));
        }

        @Test
        @DisplayName("rejects a URL that only looks absolute")
        void rejectsGarbage() throws Exception {
            createMovie("https://KXEb3yzz0D5lXYdk;[#`E(G@")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.posterUrl").exists())
                    .andExpect(jsonPath("$.violations[0].field").value("posterUrl"))
                    .andExpect(jsonPath("$.violations[0].code").value("HttpUrl"));
        }

        @Test
        @DisplayName("accepts a real image URL")
        void acceptsRealUrl() throws Exception {
            createMovie("https://image.tmdb.org/t/p/w500/x.jpg")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/x.jpg"));
        }
    }

    @Nested
    @DisplayName("B3/B4 problem envelope")
    class ProblemEnvelope {

        @Test
        @DisplayName("publishes machine-readable violations with constraint parameters")
        void violations() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"Grace","lastName":"Hopper","email":"grace@example.com","password":"abc"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.violations[0].field").value("password"))
                    .andExpect(jsonPath("$.violations[0].code").value("Size"))
                    .andExpect(jsonPath("$.violations[0].params.min").value(8))
                    .andExpect(jsonPath("$.violations[0].params.max").value(72));
        }

        @Test
        @DisplayName("reports a binding failure with its own code")
        void typeMismatch() throws Exception {
            mockMvc.perform(get("/api/screenings").param("from", "yesterday"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").isNotEmpty());
        }

        @Test
        @DisplayName("sets a stable type on domain, framework and security errors")
        void typeEverywhere() throws Exception {
            mockMvc.perform(get("/api/screenings/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "not-found"));
            mockMvc.perform(get("/api/reservations"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-credentials"));
            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "forbidden"));
            mockMvc.perform(post("/api/metadata/config"))
                    .andExpect(jsonPath("$.type").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("B5 archive instead of delete")
    class Archive {

        @Test
        @DisplayName("archives a screening whose tickets were all cancelled, keeping its reservations")
        void archivesCancelledScreening() throws Exception {
            String customer = bearer(CUSTOMER_EMAIL);
            String admin = bearer(ADMIN_EMAIL);
            String reservation = book(customer, seatA1);
            mockMvc.perform(post("/api/reservations/{id}/cancellation", reservation)
                            .header(HttpHeaders.AUTHORIZATION, customer))
                    .andExpect(status().isOk());

            archive(admin, "screenings", screening.getUuid(), true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true));

            mockMvc.perform(get("/api/screenings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].uuid", not(hasItem(screening.getUuid().toString()))));
            mockMvc.perform(get("/api/screenings").param("includeArchived", "true"))
                    .andExpect(jsonPath("$.content[*].uuid", hasItem(screening.getUuid().toString())));
            mockMvc.perform(get("/api/screenings/{id}", screening.getUuid()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true));
            mockMvc.perform(get("/api/reservations/{id}", reservation).header(HttpHeaders.AUTHORIZATION, customer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.movieScreeningId").value(screening.getUuid().toString()));
        }

        @Test
        @DisplayName("refuses to archive an upcoming screening with active tickets")
        void refusesSoldScreening() throws Exception {
            book(bearer(CUSTOMER_EMAIL), seatA1);
            archive(bearer(ADMIN_EMAIL), "screenings", screening.getUuid(), true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        }

        @Test
        @DisplayName("rejects holds, quotes and bookings on an archived screening")
        void archivedScreeningIsClosed() throws Exception {
            archive(bearer(ADMIN_EMAIL), "screenings", screening.getUuid(), true).andExpect(status().isOk());
            String customer = bearer(CUSTOMER_EMAIL);

            hold(customer, screening, seatA1)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("RESOURCE_ARCHIVED"))
                    .andExpect(jsonPath("$.context.resource").value("Movie screening"));
            mockMvc.perform(post("/api/reservations:quote")
                            .header(HttpHeaders.AUTHORIZATION, customer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"movieScreeningId\":\"%s\",\"seatIds\":[\"%s\"]}"
                                    .formatted(screening.getUuid(), seatA1.getUuid())))
                    .andExpect(jsonPath("$.code").value("RESOURCE_ARCHIVED"));
        }

        @Test
        @DisplayName("frees the slot of an archived screening and checks it again on restore")
        void archivingFreesTheSlot() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            archive(admin, "screenings", screening.getUuid(), true).andExpect(status().isOk());
            mockMvc.perform(post("/api/screenings")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieId":"%s","screenRoomId":"%s","startsAt":"%s","basePrice":12.00}
                                    """.formatted(movie.getUuid(), room.getUuid(), screening.getStartsAt())))
                    .andExpect(status().isCreated());

            archive(admin, "screenings", screening.getUuid(), false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCREENING_OVERLAP"));
        }

        @Test
        @DisplayName("archives a movie only when it has no upcoming screenings")
        void archivesMovie() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            archive(admin, "movies", movie.getUuid(), true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

            archive(admin, "screenings", screening.getUuid(), true).andExpect(status().isOk());
            archive(admin, "movies", movie.getUuid(), true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true));
            mockMvc.perform(get("/api/movies"))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/movies").param("includeArchived", "true"))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(post("/api/screenings")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieId":"%s","screenRoomId":"%s","startsAt":"%s","basePrice":12.00}
                                    """.formatted(movie.getUuid(), room.getUuid(), OffsetDateTime.now().plusDays(9))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("RESOURCE_ARCHIVED"));
        }

        @Test
        @DisplayName("hides an archived product from the menu and refuses to sell it")
        void archivesProduct() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            archive(admin, "inventory", popcorn.getUuid(), true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true));

            mockMvc.perform(get("/api/products")).andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/inventory").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/inventory").param("includeArchived", "true").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(post("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieScreeningId":"%s","seatIds":["%s"],"products":[{"inventoryId":"%s","quantity":1}]}
                                    """.formatted(screening.getUuid(), seatA1.getUuid(), popcorn.getUuid())))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("RESOURCE_ARCHIVED"));
        }
    }

    @Nested
    @DisplayName("B7 atomic hold replacement")
    class HoldReplacement {

        private ResultActions replace(String token, String holdId, Seat... seats) throws Exception {
            return mockMvc.perform(put("/api/holds/{id}", holdId)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(seatIds(seats)));
        }

        @Test
        @DisplayName("swaps the seat set in place and keeps the expiry")
        void replacesKeepingExpiry() throws Exception {
            String customer = bearer(CUSTOMER_EMAIL);
            String created = hold(customer, screening, seatA1).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String holdId = JsonPath.read(created, "$.uuid");

            String replaced = replace(customer, holdId, seatA1, seatA2)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uuid").value(holdId))
                    .andExpect(jsonPath("$.seatIds.length()").value(2))
                    .andReturn().getResponse().getContentAsString();
            // El alta responde con la hora en memoria (nanosegundos) y el reemplazo con la guardada
            // (microsegundos): se comparan al milisegundo.
            assertThat(OffsetDateTime.parse(JsonPath.<String>read(replaced, "$.expiresAt")).truncatedTo(ChronoUnit.MILLIS))
                    .isAtSameInstantAs(OffsetDateTime.parse(JsonPath.<String>read(created, "$.expiresAt"))
                            .truncatedTo(ChronoUnit.MILLIS));

            hold(bearer(OTHER_EMAIL), screening, seatA2)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));

            replace(customer, holdId, seatA3).andExpect(status().isOk());
            hold(bearer(OTHER_EMAIL), screening, seatA1, seatA2).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("fails like a new hold on sold seats, and 404s on someone else's hold")
        void sameErrorsAsCreate() throws Exception {
            String customer = bearer(CUSTOMER_EMAIL);
            String holdId = JsonPath.read(hold(customer, screening, seatA1).andReturn().getResponse().getContentAsString(),
                    "$.uuid");
            book(bearer(OTHER_EMAIL), seatA2);

            replace(customer, holdId, seatA1, seatA2)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));
            replace(bearer(OTHER_EMAIL), holdId, seatA3)
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("B8 seat availability stream")
    class SeatStream {

        @Test
        @DisplayName("pushes a seats event to an anonymous subscriber when someone holds a seat")
        void pushesHoldEvents() throws Exception {
            MvcResult stream = mockMvc.perform(get("/api/screenings/{id}/seats/stream", screening.getUuid())
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            await().atMost(Duration.ofSeconds(2))
                    .until(() -> seatStreamService.subscriberCount(screening.getUuid()) == 1);

            hold(bearer(CUSTOMER_EMAIL), screening, seatA1).andExpect(status().isCreated());

            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                String body = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(body).contains("event:seats")
                        .contains("\"seatId\":\"" + seatA1.getUuid() + "\"")
                        .contains("\"available\":false")
                        .contains("\"held\":true");
            });
        }

        @Test
        @DisplayName("answers 404 before opening a stream for an unknown screening")
        void unknownScreening() throws Exception {
            mockMvc.perform(get("/api/screenings/{id}/seats/stream", UUID.randomUUID())
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("B9 now showing")
    class NowShowing {

        @Test
        @DisplayName("groups upcoming screenings by movie in chronological order")
        void aggregates() throws Exception {
            MovieScreening earlier = screeningAt(OffsetDateTime.now().plusDays(1));
            movieRepository.save(Movie.builder()
                    .movieName("Unscheduled")
                    .genres(new ArrayList<>(List.of(MovieGenre.DRAMA)))
                    .runtime(90)
                    .build());

            mockMvc.perform(get("/api/movies/now-showing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].movie.movieName").value("Arrival"))
                    .andExpect(jsonPath("$.content[0].screenings[*].uuid",
                            contains(earlier.getUuid().toString(), screening.getUuid().toString())))
                    .andExpect(jsonPath("$.content[0].screenings[0].availableSeats").value(3));

            mockMvc.perform(get("/api/movies/now-showing")
                            .param("to", OffsetDateTime.now().plusDays(1).plusHours(1).toString()))
                    .andExpect(jsonPath("$.content[0].screenings.length()").value(1));
        }
    }

    @Nested
    @DisplayName("B10 admin user search")
    class UserSearch {

        @Test
        @DisplayName("matches part of the e-mail or of the name")
        void partialMatches() throws Exception {
            String admin = bearer(ADMIN_EMAIL);
            mockMvc.perform(get("/api/users").param("email", "ADA@").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value(CUSTOMER_EMAIL));
            mockMvc.perform(get("/api/users").param("name", "babb").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[0].email").value(OTHER_EMAIL));
            mockMvc.perform(get("/api/users").param("name", "ada love").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.content[0].email").value(CUSTOMER_EMAIL));
            mockMvc.perform(get("/api/users").param("email", "%").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("B12 screening with its seat map")
    class IncludeSeatMap {

        @Test
        @DisplayName("embeds the seat map only when asked to")
        void embedsSeatMap() throws Exception {
            mockMvc.perform(get("/api/screenings/{id}", screening.getUuid()).param("include", "seatMap"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seatMap.screeningId").value(screening.getUuid().toString()))
                    .andExpect(jsonPath("$.seatMap.seats.length()").value(3));
            mockMvc.perform(get("/api/screenings/{id}", screening.getUuid()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seatMap").doesNotExist());
        }

        @Test
        @DisplayName("rejects an unknown include value")
        void rejectsUnknown() throws Exception {
            mockMvc.perform(get("/api/screenings/{id}", screening.getUuid()).param("include", "everything"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("include"))
                    .andExpect(jsonPath("$.violations[0].code").value("OneOf"))
                    .andExpect(jsonPath("$.violations[0].params.values[0]").value("seatMap"));
        }
    }
}
