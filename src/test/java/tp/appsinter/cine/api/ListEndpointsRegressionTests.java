package tp.appsinter.cine.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.inventory.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static tp.appsinter.cine.support.TestUsers.ADMIN;

/**
 * Recorre todos los listados paginados contra PostgreSQL real: sin parámetros, con cada filtro
 * documentado por separado y con cada propiedad de orden publicada, ascendente y descendente.
 *
 * <p>Existe porque un {@code (:from is null or …)} con un {@code OffsetDateTime} hizo que
 * {@code GET /api/screenings} respondiera 500 a toda petición en PostgreSQL —«could not determine
 * data type of parameter»— y ninguna prueba lo llamó sin filtros. Los listados, sus filtros y sus
 * órdenes salen de {@code /v3/api-docs}, así que un listado nuevo queda cubierto sin tocar esta
 * prueba; cada llamada es una prueba dinámica con su propio nombre.
 */
@ApiIntegrationTest
@DisplayName("List endpoints regression")
class ListEndpointsRegressionTests {

    private static final String PAGE_SCHEMA_PREFIX = "#/components/schemas/PageResponseDTO";
    private static final List<String> PAGING = List.of("page", "size", "sort");

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    /**
     * Valor de cada filtro que nombra un recurso, por nombre del parámetro.
     */
    private final Map<String, String> filterIds = new LinkedHashMap<>();
    /**
     * Recurso con que se completa un listado anidado, por prefijo de la ruta.
     */
    private final Map<String, String> pathIds = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        TestUsers.createAll(userService);
        Catalogue catalogue = fixture.create(SeatType.NORMAL);
        UUID popcornId = catalogue.popcorn().getUuid();

        assertThat(mvc.post().uri("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, api.bearer(TestUsers.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(new ReservationRequestDTO(catalogue.screening().getUuid(),
                                List.of(catalogue.seat(1).getUuid()),
                                List.of(new ReservationItemRequestDTO(popcornId, 1))))))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/inventory/{id}/adjustments", popcornId)
                        .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(new InventoryAdjustmentRequestDTO(5, "Restock"))))
                .hasStatusOk();

        filterIds.put("movieId", catalogue.movie().getUuid().toString());
        filterIds.put("roomId", catalogue.room().getUuid().toString());
        filterIds.put("screeningId", catalogue.screening().getUuid().toString());
        filterIds.put("userId", userRepository.findByEmailIgnoreCase(TestUsers.CUSTOMER).orElseThrow().getUuid().toString());
        pathIds.put("/api/inventory/", popcornId.toString());
    }

    @TestFactory
    @DisplayName("every documented list answers 200 with no filter, each filter alone and each sort")
    Stream<DynamicNode> everyListAnswers() throws IOException {
        MvcTestResult apiDocs = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(apiDocs).hasStatusOk();
        DocumentContext docs = JsonPath.parse(apiDocs.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String, Object> paths = docs.read("$.paths");

        List<DynamicContainer> lists = new ArrayList<>();
        paths.forEach((template, item) -> {
            if (item instanceof Map<?, ?> pathItem && pathItem.get("get") instanceof Map<?, ?> operation
                    && returnsPage(operation)) {
                lists.add(dynamicContainer(template, callsFor(docs, resolvePath(template), operation)));
            }
        });

        assertThat(lists).extracting(DynamicNode::getDisplayName)
                .as("list endpoints discovered in /v3/api-docs")
                .contains("/api/movies", "/api/screenings", "/api/reservations", "/api/reservations/all",
                        "/api/inventory/{uuid}/adjustments", "/api/admin/contact-messages");
        return lists.stream().map(DynamicNode.class::cast);
    }

    private Stream<DynamicTest> callsFor(DocumentContext docs, String path, Map<?, ?> operation) {
        List<Map<?, ?>> parameters = parametersOf(operation);
        List<DynamicTest> calls = new ArrayList<>();
        calls.add(call(path, Map.of()));
        for (Map<?, ?> parameter : parameters) {
            if (!"query".equals(parameter.get("in")) || !(parameter.get("name") instanceof String name)
                    || PAGING.contains(name)) {
                continue;
            }
            calls.add(call(path, Map.of(name, sampleValue(docs, name, parameter.get("schema")))));
        }
        for (String property : sortProperties(parameters)) {
            calls.add(call(path, Map.of("sort", property + ",asc")));
            calls.add(call(path, Map.of("sort", property + ",desc")));
        }
        return calls.stream();
    }

    private DynamicTest call(String path, Map<String, String> params) {
        return dynamicTest(params.isEmpty() ? "no parameters" : params.toString(), () -> {
            var request = mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN));
            params.forEach(request::param);
            MvcTestResult result = request.exchange();
            assertThat(result.getResponse().getStatus())
                    .as("%s %s -> %s", path, params, result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .isEqualTo(HttpStatus.OK.value());
        });
    }

    private static boolean returnsPage(Map<?, ?> operation) {
        return operation.get("responses") instanceof Map<?, ?> responses
                && responses.get("200") instanceof Map<?, ?> ok
                && ok.get("content") instanceof Map<?, ?> content
                && content.values().stream().anyMatch(media -> media instanceof Map<?, ?> mediaType
                        && mediaType.get("schema") instanceof Map<?, ?> schema
                        && String.valueOf(schema.get("$ref")).startsWith(PAGE_SCHEMA_PREFIX));
    }

    /**
     * Los listados anidados ({@code /api/inventory/{uuid}/adjustments}) usan un recurso sembrado.
     */
    private String resolvePath(String template) {
        if (!template.contains("{")) {
            return template;
        }
        String id = pathIds.entrySet().stream()
                .filter(sample -> template.startsWith(sample.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sample id for " + template));
        return template.replaceAll("\\{[^}]+}", id);
    }

    private static List<Map<?, ?>> parametersOf(Map<?, ?> operation) {
        if (!(operation.get("parameters") instanceof List<?> parameters)) {
            return List.of();
        }
        return parameters.stream()
                .<Map<?, ?>>mapMulti((parameter, sink) -> {
                    if (parameter instanceof Map<?, ?> map) {
                        sink.accept(map);
                    }
                })
                .toList();
    }

    private static List<String> sortProperties(List<Map<?, ?>> parameters) {
        return parameters.stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .<String>mapMulti((parameter, sink) -> {
                    if (parameter.get("x-sort-properties") instanceof List<?> properties) {
                        properties.forEach(property -> sink.accept(property.toString()));
                    }
                })
                .toList();
    }

    /**
     * Un valor válido y representativo para el filtro, según su esquema: identificadores sembrados,
     * fechas ISO, booleanos, el primer valor de cada enumeración y texto corto.
     */
    private String sampleValue(DocumentContext docs, String name, @Nullable Object schemaNode) {
        if (!(schemaNode instanceof Map<?, ?> schema)) {
            return "a";
        }
        if (schema.get("$ref") instanceof String ref) {
            List<String> values = docs.read("$.components.schemas." + ref.substring(ref.lastIndexOf('/') + 1) + ".enum");
            return values.getFirst();
        }
        if ("array".equals(typeOf(schema))) {
            return sampleValue(docs, name, schema.get("items"));
        }
        if (schema.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.getFirst().toString();
        }
        return switch (typeOf(schema)) {
            case "boolean" -> "true";
            case "integer", "number" -> "releaseYear".equals(name) ? "2016" : "1";
            default -> switch (String.valueOf(schema.get("format"))) {
                case "uuid" -> filterIds.getOrDefault(name, UUID.randomUUID().toString());
                case "date-time" -> fixture.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                default -> "a";
            };
        };
    }

    private static String typeOf(Map<?, ?> schema) {
        return switch (schema.get("type")) {
            case List<?> types -> types.stream().map(Object::toString)
                    .filter(type -> !"null".equals(type)).findFirst().orElse("string");
            case null -> "string";
            case Object type -> type.toString();
        };
    }
}
