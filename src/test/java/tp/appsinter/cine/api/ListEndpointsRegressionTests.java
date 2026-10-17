package tp.appsinter.cine.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.CreateUserRequestDTO;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recorre todos los listados paginados contra PostgreSQL real: sin parámetros, con cada filtro
 * documentado por separado y con cada propiedad de orden publicada, ascendente y descendente.
 *
 * <p>Existe porque un {@code (:from is null or …)} con un {@code OffsetDateTime} hizo que
 * {@code GET /api/screenings} respondiera 500 a toda petición en PostgreSQL —«could not determine
 * data type of parameter»— y ninguna prueba lo llamó sin filtros. Los listados, sus filtros y sus
 * órdenes salen de {@code /v3/api-docs}, así que un listado nuevo queda cubierto sin tocar esta
 * prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("List endpoints regression")
class ListEndpointsRegressionTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String CUSTOMER_EMAIL = "ada@example.com";
    private static final String PASSWORD = "supersecret";
    private static final String PAGE_SCHEMA_PREFIX = "#/components/schemas/PageResponseDTO";
    private static final List<String> PAGING = List.of("page", "size", "sort");

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

    private final Map<String, String> sampleIds = new java.util.HashMap<>();
    private String adminToken;

    @BeforeEach
    void seed() {
        DatabaseCleaner.truncate(jdbcTemplate);
        UUID customerId = userService.createUser(
                new CreateUserRequestDTO("Ada", "Lovelace", CUSTOMER_EMAIL, PASSWORD), Role.CUSTOMER).uuid();
        userService.createUser(new CreateUserRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);

        Movie movie = movieRepository.save(Movie.builder()
                .movieName("Arrival")
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(116)
                .releaseYear(2016)
                .mainCast(new ArrayList<>(List.of("Amy Adams")))
                .build());
        ScreenRoom room = screenRoomRepository.save(ScreenRoom.builder()
                .roomName("Room 1")
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());
        Seat seat = seatRepository.save(Seat.builder()
                .position(new Position("A", "1"))
                .seatType(SeatType.NORMAL)
                .screenRoom(room)
                .build());
        MovieScreening screening = movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(OffsetDateTime.now().plusDays(2))
                .endsAt(OffsetDateTime.now().plusDays(2).plusMinutes(131))
                .basePrice(new BigDecimal("10.00"))
                .build());
        Inventory popcorn = inventoryRepository.save(Inventory.builder()
                .productName("Popcorn")
                .category(ProductCategory.SNACK)
                .cost(new BigDecimal("4.00"))
                .quantity(10)
                .build());

        adminToken = bearer(ADMIN_EMAIL);
        assertThat(mvc.post().uri("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieScreeningId":"%s","seatIds":["%s"],"products":[{"inventoryId":"%s","quantity":1}]}
                                """.formatted(screening.getUuid(), seat.getUuid(), popcorn.getUuid())))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/inventory/{id}/adjustments", popcorn.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5,\"reason\":\"Restock\"}"))
                .hasStatusOk();

        sampleIds.put("movieId", movie.getUuid().toString());
        sampleIds.put("roomId", room.getUuid().toString());
        sampleIds.put("screeningId", screening.getUuid().toString());
        sampleIds.put("userId", customerId.toString());
        sampleIds.put("/api/inventory", popcorn.getUuid().toString());
    }

    private String bearer(String email) {
        return "Bearer " + assertThat(mvc.post().uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.token").asString().actual();
    }

    @Test
    @DisplayName("every documented list answers 200 with no filter, each filter alone and each sort")
    void everyListAnswers() throws IOException {
        MvcTestResult apiDocs = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(apiDocs).hasStatusOk();
        DocumentContext docs = JsonPath.parse(apiDocs.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> paths = docs.read("$.paths");

        List<String> calls = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int lists = 0;
        for (var entry : paths.entrySet()) {
            Map<String, Object> operation = asMap(entry.getValue().get("get"));
            if (operation == null || !returnsPage(operation)) {
                continue;
            }
            lists++;
            String path = resolvePath(entry.getKey());
            List<Map<String, Object>> parameters = parametersOf(operation);

            check(path, Map.of(), calls, failures);
            for (Map<String, Object> parameter : parameters) {
                String name = (String) parameter.get("name");
                if (!"query".equals(parameter.get("in")) || PAGING.contains(name)) {
                    continue;
                }
                check(path, Map.of(name, sampleValue(docs, name, asMap(parameter.get("schema")))), calls, failures);
            }
            for (String property : sortProperties(parameters)) {
                check(path, Map.of("sort", property + ",asc"), calls, failures);
                check(path, Map.of("sort", property + ",desc"), calls, failures);
            }
        }

        assertThat(lists).as("list endpoints discovered in /v3/api-docs").isGreaterThanOrEqualTo(10);
        assertThat(calls).hasSizeGreaterThan(60);
        assertThat(failures).as("calls that did not answer 200").isEmpty();
    }

    private void check(String path, Map<String, String> params, List<String> calls, List<String> failures)
            throws IOException {
        var request = mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, adminToken);
        params.forEach(request::param);
        MvcTestResult result = request.exchange();
        String call = path + " " + params;
        calls.add(call);
        if (result.getResponse().getStatus() != 200) {
            failures.add(call + " -> " + result.getResponse().getStatus() + " "
                    + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
    }

    private static boolean returnsPage(Map<String, Object> operation) {
        Map<String, Object> ok = asMap(asMap(operation.get("responses")).get("200"));
        if (ok == null || ok.get("content") == null) {
            return false;
        }
        return asMap(ok.get("content")).values().stream()
                .map(media -> asMap(asMap(media).get("schema")))
                .anyMatch(schema -> schema != null && String.valueOf(schema.get("$ref")).startsWith(PAGE_SCHEMA_PREFIX));
    }

    /**
     * Los listados anidados ({@code /api/inventory/{uuid}/adjustments}) usan un recurso sembrado.
     */
    private String resolvePath(String template) {
        if (!template.contains("{")) {
            return template;
        }
        String id = sampleIds.entrySet().stream()
                .filter(sample -> template.startsWith(sample.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sample id for " + template));
        return template.replaceAll("\\{[^}]+}", id);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parametersOf(Map<String, Object> operation) {
        Object parameters = operation.get("parameters");
        return parameters == null ? List.of() : (List<Map<String, Object>>) parameters;
    }

    @SuppressWarnings("unchecked")
    private static List<String> sortProperties(List<Map<String, Object>> parameters) {
        return parameters.stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .map(parameter -> (List<String>) parameter.get("x-sort-properties"))
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Un valor válido y representativo para el filtro, según su esquema: identificadores sembrados,
     * fechas ISO, booleanos, el primer valor de cada enumeración y texto corto.
     */
    private String sampleValue(DocumentContext docs, String name, Map<String, Object> schema) {
        if (schema == null) {
            return "a";
        }
        Object ref = schema.get("$ref");
        if (ref != null) {
            List<String> values = docs.read("$.components.schemas." + ref.toString().substring(ref.toString().lastIndexOf('/') + 1) + ".enum");
            return values.getFirst();
        }
        if ("array".equals(typeOf(schema))) {
            return sampleValue(docs, name, asMap(schema.get("items")));
        }
        if (schema.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.getFirst().toString();
        }
        String format = String.valueOf(schema.get("format"));
        return switch (typeOf(schema)) {
            case "boolean" -> "true";
            case "integer", "number" -> "releaseYear".equals(name) ? "2016" : "1";
            default -> switch (format) {
                case "uuid" -> sampleIds.getOrDefault(name, UUID.randomUUID().toString());
                case "date-time" -> OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                default -> "a";
            };
        };
    }

    private static String typeOf(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof List<?> types) {
            return types.stream().map(Object::toString).filter(t -> !"null".equals(t)).findFirst().orElse("string");
        }
        return type == null ? "string" : type.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
