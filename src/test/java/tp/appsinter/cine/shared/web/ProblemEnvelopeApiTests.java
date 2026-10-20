package tp.appsinter.cine.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

/**
 * La forma de los errores (RFC 9457) que comparten todos los endpoints: {@code type} estable,
 * {@code code} legible por máquinas y, en los de validación, el detalle por campo.
 */
@ApiIntegrationTest
@DisplayName("Problem envelope")
class ProblemEnvelopeApiTests {

    private static final String PROBLEMS = ProblemProperties.DEFAULT_TYPE_BASE;

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("publishes machine-readable violations with constraint parameters")
        void violations() {
            assertThat(mvc.post().uri("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new RegisterRequestDTO("Grace", "Hopper", "grace@example.com", "abc"))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.type", type -> assertThat(type).isEqualTo(PROBLEMS + "validation-failed"))
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPath("$.errors.password")
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("password"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("Size"))
                    .hasPathSatisfying("$.violations[0].params.min", min -> assertThat(min).isEqualTo(8))
                    .hasPathSatisfying("$.violations[0].params.max", max -> assertThat(max).isEqualTo(72));
        }

        @Test
        @DisplayName("rejects a field longer than its column as a validation error, not a conflict")
        void oversizedField() {
            assertThat(mvc.post().uri("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new RegisterRequestDTO("x".repeat(256), "Hopper", "grace@example.com",
                                    TestUsers.PASSWORD))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPath("$.errors.firstName");
        }
    }

    @Nested
    @DisplayName("malformed requests")
    class MalformedRequests {

        @Test
        @DisplayName("reports a query parameter of the wrong type")
        void typeMismatch() {
            assertThat(mvc.get().uri("/api/screenings").param("from", "yesterday"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("BAD_REQUEST"))
                    .hasPathSatisfying("$.type", type -> assertThat(type).isEqualTo(PROBLEMS + "bad-request"));
        }

        @Test
        @DisplayName("reports a path variable of the wrong type")
        void pathTypeMismatch() {
            assertThat(mvc.get().uri("/api/movies/not-a-uuid"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
        }

        @Test
        @DisplayName("reports a body that is not JSON")
        void malformedBody() {
            assertThat(mvc.post().uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
        }
    }

    @Nested
    @DisplayName("stable types")
    class StableTypes {

        @Test
        @DisplayName("sets a stable type on domain, framework and security errors")
        void typeEverywhere() {
            assertThat(mvc.get().uri("/api/screenings/{id}", UUID.randomUUID()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "not-found");
            assertThat(mvc.get().uri("/api/reservations"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.type", type -> assertThat(type).isEqualTo(PROBLEMS + "invalid-credentials"))
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo(401))
                    .hasPathSatisfying("$.detail", detail -> assertThat(detail).asString().isNotEmpty());
            assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "forbidden");
            // Anónimo, la seguridad cortaría antes con 401: sólo GET y HEAD son públicos.
            assertThat(mvc.post().uri("/api/metadata/config").header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("METHOD_NOT_ALLOWED"))
                    .hasPathSatisfying("$.type", type -> assertThat(type).isEqualTo(PROBLEMS + "method-not-allowed"));
        }
    }

    @Nested
    @DisplayName("paging and sorting")
    class PagingAndSorting {

        @Test
        @DisplayName("translates an unknown sort property instead of leaking the query")
        void unknownSortProperty() {
            assertThat(mvc.get().uri("/api/movies").param("sort", "bogus,asc"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("INVALID_SORT_PROPERTY"))
                    .hasPathSatisfying("$.detail", detail -> assertThat(detail).isEqualTo("Unknown sort property 'bogus'"));
        }

        @Test
        @DisplayName("refuses to sort by a property the listing does not publish")
        void unpublishedSortProperty() {
            assertThat(mvc.get().uri("/api/users").param("sort", "password")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("INVALID_SORT_PROPERTY"))
                    .hasPathSatisfying("$.context.property", property -> assertThat(property).isEqualTo("password"));
        }

        @Test
        @DisplayName("caps the page size")
        void capsPageSize() {
            assertThat(mvc.get().uri("/api/users").param("size", "5000")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.size").isEqualTo(100);
        }
    }
}
