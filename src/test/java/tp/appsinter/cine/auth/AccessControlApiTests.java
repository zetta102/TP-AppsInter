package tp.appsinter.cine.auth;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

@ApiIntegrationTest
@DisplayName("Access control")
class AccessControlApiTests {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
    }

    /**
     * Quién hace la petición; {@code NONE} es una petición anónima.
     */
    enum Caller {
        NONE(null), CUSTOMER(TestUsers.CUSTOMER), ADMIN(TestUsers.ADMIN);

        private final @Nullable String email;

        Caller(@Nullable String email) {
            this.email = email;
        }
    }

    private static Arguments rule(HttpMethod method, String path, Caller caller, HttpStatus expected) {
        return argumentSet("%s %s as %s → %d".formatted(method, path, caller, expected.value()),
                method, path, caller, expected);
    }

    /**
     * Una fila por regla de {@code SecurityConfig}. Sólo se verifica que la regla deje pasar o corte;
     * lo que responde el endpoint después es asunto de otras pruebas, por eso los casos permitidos
     * no usan identificadores reales.
     */
    static Stream<Arguments> rules() {
        return Stream.of(
                rule(HttpMethod.GET, "/api/movies", Caller.NONE, HttpStatus.OK),
                rule(HttpMethod.HEAD, "/api/movies", Caller.NONE, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/products", Caller.NONE, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/metadata/enums", Caller.NONE, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/reservations", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.POST, "/api/reservations:quote", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.DELETE, "/api/holds/" + UUID.randomUUID(), Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.GET, "/api/inventory", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.GET, "/api/users", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.POST, "/api/movies", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.GET, "/api/contact-messages", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.GET, "/api/admin/contact-messages", Caller.NONE, HttpStatus.UNAUTHORIZED),
                rule(HttpMethod.GET, "/api/reservations", Caller.CUSTOMER, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/users/me", Caller.CUSTOMER, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/contact-messages", Caller.CUSTOMER, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/inventory", Caller.CUSTOMER, HttpStatus.FORBIDDEN),
                rule(HttpMethod.GET, "/api/users", Caller.CUSTOMER, HttpStatus.FORBIDDEN),
                rule(HttpMethod.GET, "/api/reservations/all", Caller.CUSTOMER, HttpStatus.FORBIDDEN),
                rule(HttpMethod.GET, "/api/admin/contact-messages", Caller.CUSTOMER, HttpStatus.FORBIDDEN),
                rule(HttpMethod.POST, "/api/movies", Caller.CUSTOMER, HttpStatus.FORBIDDEN),
                rule(HttpMethod.GET, "/api/inventory", Caller.ADMIN, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/users", Caller.ADMIN, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/reservations/all", Caller.ADMIN, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/contact-messages", Caller.ADMIN, HttpStatus.OK),
                rule(HttpMethod.GET, "/api/admin/contact-messages", Caller.ADMIN, HttpStatus.OK));
    }

    @ParameterizedTest
    @MethodSource("rules")
    @DisplayName("access matrix")
    void enforcesRules(HttpMethod method, String path, Caller caller, HttpStatus expected) {
        var request = mvc.method(method).uri(path);
        if (HttpMethod.POST.equals(method)) {
            request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (caller.email != null) {
            request.header(HttpHeaders.AUTHORIZATION, api.bearer(caller.email));
        }
        assertThat(request).hasStatus(expected);
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("returns a coded problem detail when a customer reaches an admin endpoint")
        void forbiddenBody() {
            assertThat(mvc.get().uri("/api/reservations/all").header(HttpHeaders.AUTHORIZATION, api.bearer(TestUsers.CUSTOMER)))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo(403))
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("FORBIDDEN"));
        }

        @Test
        @DisplayName("blocks a customer from reading another user by id, but not themselves")
        void blocksCustomerLookupById() {
            UUID adminId = userRepository.findByEmailIgnoreCase(TestUsers.ADMIN).orElseThrow().getUuid();

            assertThat(mvc.get().uri("/api/users/{id}", adminId)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(TestUsers.CUSTOMER)))
                    .hasStatus(HttpStatus.FORBIDDEN);
            assertThat(mvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, api.bearer(TestUsers.CUSTOMER)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.email").isEqualTo(TestUsers.CUSTOMER);
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("lets the frontend preflight a HEAD request")
        void allowsHeadPreflight() {
            assertThat(mvc.options().uri("/api/movies")
                            .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.HEAD.name()))
                    .hasStatusOk()
                    .headers()
                    .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN);
        }

        @Test
        @DisplayName("refuses a preflight from an unknown origin")
        void refusesUnknownOrigin() {
            assertThat(mvc.options().uri("/api/movies")
                            .header(HttpHeaders.ORIGIN, "https://evil.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
