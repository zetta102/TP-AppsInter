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
import tp.appsinter.cine.room.Position;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomRepository;
import tp.appsinter.cine.room.ScreenRoomType;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.screening.SeatStreamService;
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.CreateUserRequestDTO;

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
    private MockMvcTester mvc;
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

    private static String seatIds(Seat... seats) {
        return "{\"seatIds\":[%s]}".formatted(String.join(",",
                Arrays.stream(seats).map(seat -> "\"" + seat.getUuid() + "\"").toList()));
    }

    private MockMvcTester.MockMvcRequestBuilder hold(String token, MovieScreening target, Seat... seats) {
        return mvc.post().uri("/api/screenings/{id}/holds", target.getUuid())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatIds(seats));
    }

    private String book(String token, Seat... seats) {
        MvcTestResult booked = mvc.post().uri("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"movieScreeningId":"%s","seatIds":%s}
                        """.formatted(screening.getUuid(),
                        seatIds(seats).replace("{\"seatIds\":", "").replace("]}", "]")))
                .exchange();
        assertThat(booked).hasStatus(HttpStatus.CREATED);
        return read(booked, "$.uuid");
    }

    private MockMvcTester.MockMvcRequestBuilder archive(String token, String resource, UUID id, boolean archived) {
        return mvc.patch().uri("/api/{resource}/{id}", resource, id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":%s}".formatted(archived));
    }

    @Nested
    @DisplayName("B2 movie URL validation")
    class UrlValidation {

        private MockMvcTester.MockMvcRequestBuilder createMovie(String posterUrl) {
            return mvc.post().uri("/api/movies")
                    .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"movieName":"Dune","genres":["SCI_FI"],"runtime":155,"posterUrl":"%s"}
                            """.formatted(posterUrl));
        }

        @Test
        @DisplayName("rejects a URL that only looks absolute")
        void rejectsGarbage() {
            assertThat(createMovie("https://KXEb3yzz0D5lXYdk;[#`E(G@"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPath("$.errors.posterUrl")
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("posterUrl"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("HttpUrl"));
        }

        @Test
        @DisplayName("accepts a real image URL")
        void acceptsRealUrl() {
            assertThat(createMovie("https://image.tmdb.org/t/p/w500/x.jpg"))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.posterUrl").isEqualTo("https://image.tmdb.org/t/p/w500/x.jpg");
        }
    }

    @Nested
    @DisplayName("B3/B4 problem envelope")
    class ProblemEnvelope {

        @Test
        @DisplayName("publishes machine-readable violations with constraint parameters")
        void violations() {
            assertThat(mvc.post().uri("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"Grace","lastName":"Hopper","email":"grace@example.com","password":"abc"}
                                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.type", type -> assertThat(type).isEqualTo(PROBLEMS + "validation-failed"))
                    .hasPath("$.errors.password")
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("password"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("Size"))
                    .hasPathSatisfying("$.violations[0].params.min", min -> assertThat(min).isEqualTo(8))
                    .hasPathSatisfying("$.violations[0].params.max", max -> assertThat(max).isEqualTo(72));
        }

        @Test
        @DisplayName("reports a binding failure with its own code")
        void typeMismatch() {
            assertThat(mvc.get().uri("/api/screenings").param("from", "yesterday"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.type").isNotEmpty();
        }

        @Test
        @DisplayName("sets a stable type on domain, framework and security errors")
        void typeEverywhere() {
            assertThat(mvc.get().uri("/api/screenings/{id}", UUID.randomUUID()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "not-found");
            assertThat(mvc.get().uri("/api/reservations"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-credentials");
            assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "forbidden");
            assertThat(mvc.post().uri("/api/metadata/config"))
                    .bodyJson().extractingPath("$.type").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("B5 archive instead of delete")
    class Archive {

        @Test
        @DisplayName("archives a screening whose tickets were all cancelled, keeping its reservations")
        void archivesCancelledScreening() {
            String customer = bearer(CUSTOMER_EMAIL);
            String admin = bearer(ADMIN_EMAIL);
            String reservation = book(customer, seatA1);
            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservation)
                            .header(HttpHeaders.AUTHORIZATION, customer))
                    .hasStatusOk();

            assertThat(archive(admin, "screenings", screening.getUuid(), true))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);

            assertThat(mvc.get().uri("/api/screenings"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].uuid").asArray()
                    .doesNotContain(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings").param("includeArchived", "true"))
                    .bodyJson().extractingPath("$.content[*].uuid").asArray()
                    .contains(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);
            assertThat(mvc.get().uri("/api/reservations/{id}", reservation).header(HttpHeaders.AUTHORIZATION, customer))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.movieScreeningId").isEqualTo(screening.getUuid().toString());
        }

        @Test
        @DisplayName("refuses to archive an upcoming screening with active tickets")
        void refusesSoldScreening() {
            book(bearer(CUSTOMER_EMAIL), seatA1);
            assertThat(archive(bearer(ADMIN_EMAIL), "screenings", screening.getUuid(), true))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_IN_USE");
        }

        @Test
        @DisplayName("rejects holds, quotes and bookings on an archived screening")
        void archivedScreeningIsClosed() {
            assertThat(archive(bearer(ADMIN_EMAIL), "screenings", screening.getUuid(), true)).hasStatusOk();
            String customer = bearer(CUSTOMER_EMAIL);

            assertThat(hold(customer, screening, seatA1))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_ARCHIVED"))
                    .hasPathSatisfying("$.context.resource", resource -> assertThat(resource).isEqualTo("Movie screening"));
            assertThat(mvc.post().uri("/api/reservations:quote")
                            .header(HttpHeaders.AUTHORIZATION, customer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"movieScreeningId\":\"%s\",\"seatIds\":[\"%s\"]}"
                                    .formatted(screening.getUuid(), seatA1.getUuid())))
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
        }

        @Test
        @DisplayName("frees the slot of an archived screening and checks it again on restore")
        void archivingFreesTheSlot() {
            String admin = bearer(ADMIN_EMAIL);
            assertThat(archive(admin, "screenings", screening.getUuid(), true)).hasStatusOk();
            assertThat(mvc.post().uri("/api/screenings")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieId":"%s","screenRoomId":"%s","startsAt":"%s","basePrice":12.00}
                                    """.formatted(movie.getUuid(), room.getUuid(), screening.getStartsAt())))
                    .hasStatus(HttpStatus.CREATED);

            assertThat(archive(admin, "screenings", screening.getUuid(), false))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SCREENING_OVERLAP");
        }

        @Test
        @DisplayName("archives a movie only when it has no upcoming screenings")
        void archivesMovie() {
            String admin = bearer(ADMIN_EMAIL);
            assertThat(archive(admin, "movies", movie.getUuid(), true))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_IN_USE");

            assertThat(archive(admin, "screenings", screening.getUuid(), true)).hasStatusOk();
            assertThat(archive(admin, "movies", movie.getUuid(), true))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);
            assertThat(mvc.get().uri("/api/movies"))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/movies").param("includeArchived", "true"))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.post().uri("/api/screenings")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieId":"%s","screenRoomId":"%s","startsAt":"%s","basePrice":12.00}
                                    """.formatted(movie.getUuid(), room.getUuid(), OffsetDateTime.now().plusDays(9))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
        }

        @Test
        @DisplayName("hides an archived product from the menu and refuses to sell it")
        void archivesProduct() {
            String admin = bearer(ADMIN_EMAIL);
            assertThat(archive(admin, "inventory", popcorn.getUuid(), true))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);

            assertThat(mvc.get().uri("/api/products"))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/inventory").header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/inventory").param("includeArchived", "true")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.post().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieScreeningId":"%s","seatIds":["%s"],"products":[{"inventoryId":"%s","quantity":1}]}
                                    """.formatted(screening.getUuid(), seatA1.getUuid(), popcorn.getUuid())))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
        }
    }

    @Nested
    @DisplayName("B7 atomic hold replacement")
    class HoldReplacement {

        private MockMvcTester.MockMvcRequestBuilder replace(String token, String holdId, Seat... seats) {
            return mvc.put().uri("/api/holds/{id}", holdId)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(seatIds(seats));
        }

        @Test
        @DisplayName("swaps the seat set in place and keeps the expiry")
        void replacesKeepingExpiry() {
            String customer = bearer(CUSTOMER_EMAIL);
            MvcTestResult created = hold(customer, screening, seatA1).exchange();
            assertThat(created).hasStatus(HttpStatus.CREATED);
            String holdId = read(created, "$.uuid");

            MvcTestResult replaced = replace(customer, holdId, seatA1, seatA2).exchange();
            assertThat(replaced)
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.uuid", uuid -> assertThat(uuid).isEqualTo(holdId))
                    .hasPathSatisfying("$.seatIds", ids -> assertThat(ids).asArray().hasSize(2));
            // El alta responde con la hora en memoria (nanosegundos) y el reemplazo con la guardada
            // (microsegundos): se comparan al milisegundo.
            assertThat(OffsetDateTime.parse(read(replaced, "$.expiresAt")).truncatedTo(ChronoUnit.MILLIS))
                    .isAtSameInstantAs(OffsetDateTime.parse(read(created, "$.expiresAt"))
                            .truncatedTo(ChronoUnit.MILLIS));

            assertThat(hold(bearer(OTHER_EMAIL), screening, seatA2))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");

            assertThat(replace(customer, holdId, seatA3)).hasStatusOk();
            assertThat(hold(bearer(OTHER_EMAIL), screening, seatA1, seatA2)).hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("fails like a new hold on sold seats, and 404s on someone else's hold")
        void sameErrorsAsCreate() {
            String customer = bearer(CUSTOMER_EMAIL);
            String holdId = read(hold(customer, screening, seatA1).exchange(), "$.uuid");
            book(bearer(OTHER_EMAIL), seatA2);

            assertThat(replace(customer, holdId, seatA1, seatA2))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");
            assertThat(replace(bearer(OTHER_EMAIL), holdId, seatA3))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("B8 seat availability stream")
    class SeatStream {

        @Test
        @DisplayName("pushes a seats event to an anonymous subscriber when someone holds a seat")
        void pushesHoldEvents() {
            // El canal no termina nunca: asyncExchange no espera el resultado asíncrono, que es lo
            // que haría exchange.
            MvcTestResult stream = mvc.get().uri("/api/screenings/{id}/seats/stream", screening.getUuid())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .asyncExchange();
            assertThat(stream).request().hasAsyncStarted(true);
            await().atMost(Duration.ofSeconds(2))
                    .until(() -> seatStreamService.subscriberCount(screening.getUuid()) == 1);

            assertThat(hold(bearer(CUSTOMER_EMAIL), screening, seatA1)).hasStatus(HttpStatus.CREATED);

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
        void unknownScreening() {
            assertThat(mvc.get().uri("/api/screenings/{id}/seats/stream", UUID.randomUUID())
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("B9 now showing")
    class NowShowing {

        @Test
        @DisplayName("groups upcoming screenings by movie in chronological order")
        void aggregates() {
            MovieScreening earlier = screeningAt(OffsetDateTime.now().plusDays(1));
            movieRepository.save(Movie.builder()
                    .movieName("Unscheduled")
                    .genres(new ArrayList<>(List.of(MovieGenre.DRAMA)))
                    .runtime(90)
                    .build());

            assertThat(mvc.get().uri("/api/movies/now-showing"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].movie.movieName", name -> assertThat(name).isEqualTo("Arrival"))
                    .hasPathSatisfying("$.content[0].screenings[*].uuid", ids -> assertThat(ids).asArray()
                            .containsExactly(earlier.getUuid().toString(), screening.getUuid().toString()))
                    .hasPathSatisfying("$.content[0].screenings[0].availableSeats",
                            seats -> assertThat(seats).isEqualTo(3));

            assertThat(mvc.get().uri("/api/movies/now-showing")
                            .param("to", OffsetDateTime.now().plusDays(1).plusHours(1).toString()))
                    .bodyJson().extractingPath("$.content[0].screenings").asArray().hasSize(1);
        }
    }

    @Nested
    @DisplayName("B10 admin user search")
    class UserSearch {

        @Test
        @DisplayName("matches part of the e-mail or of the name")
        void partialMatches() {
            String admin = bearer(ADMIN_EMAIL);
            assertThat(mvc.get().uri("/api/users").param("email", "ADA@").header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].email", email -> assertThat(email).isEqualTo(CUSTOMER_EMAIL));
            assertThat(mvc.get().uri("/api/users").param("name", "babb").header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson().extractingPath("$.content[0].email").isEqualTo(OTHER_EMAIL);
            assertThat(mvc.get().uri("/api/users").param("name", "ada love").header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson().extractingPath("$.content[0].email").isEqualTo(CUSTOMER_EMAIL);
            assertThat(mvc.get().uri("/api/users").param("email", "%").header(HttpHeaders.AUTHORIZATION, admin))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("B12 screening with its seat map")
    class IncludeSeatMap {

        @Test
        @DisplayName("embeds the seat map only when asked to")
        void embedsSeatMap() {
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()).param("include", "seatMap"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.seatMap.screeningId",
                            id -> assertThat(id).isEqualTo(screening.getUuid().toString()))
                    .hasPathSatisfying("$.seatMap.seats", seats -> assertThat(seats).asArray().hasSize(3));
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson().doesNotHavePath("$.seatMap");
        }

        @Test
        @DisplayName("rejects an unknown include value")
        void rejectsUnknown() {
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()).param("include", "everything"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("include"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("OneOf"))
                    .hasPathSatisfying("$.violations[0].params.values[0]",
                            value -> assertThat(value).isEqualTo("seatMap"));
        }
    }
}
