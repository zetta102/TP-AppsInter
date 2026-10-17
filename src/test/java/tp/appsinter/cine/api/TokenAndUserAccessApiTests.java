package tp.appsinter.cine.api;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.auth.RefreshTokenRepository;
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Token lifecycle and user access control")
class TokenAndUserAccessApiTests {

    private static final String CUSTOMER_EMAIL = "linus@example.com";
    private static final String ADMIN_EMAIL = "root@example.com";
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
    private RefreshTokenRepository refreshTokenRepository;

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncate(jdbcTemplate);

        userService.createUser(
                new RegisterRequestDTO("Linus", "Torvalds", CUSTOMER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(
                new RegisterRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);
    }

    private String bearer(String email) {
        return "Bearer " + assertThat(login(email)).hasStatusOk()
                .bodyJson().extractingPath("$.token").asString().actual();
    }

    private String refreshTokenFrom(MockMvcTester.MockMvcRequestBuilder request) {
        return assertThat(request).hasStatusOk()
                .bodyJson().extractingPath("$.refreshToken").asString().actual();
    }

    private MockMvcTester.MockMvcRequestBuilder login(String email) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    }

    private MockMvcTester.MockMvcRequestBuilder refresh(String refreshToken) {
        return mvc.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken));
    }

    @Nested
    @DisplayName("refresh tokens")
    class RefreshTokens {

        @Test
        @DisplayName("login returns both an access token and a refresh token")
        void loginReturnsBothTokens() {
            assertThat(login(CUSTOMER_EMAIL))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.token", token -> assertThat(token).isNotEmpty())
                    .hasPathSatisfying("$.tokenType", type -> assertThat(type).isEqualTo("Bearer"))
                    .hasPathSatisfying("$.refreshToken", token -> assertThat(token).isNotEmpty())
                    .hasPathSatisfying("$.refreshExpiresAt", expiry -> assertThat(expiry).isNotEmpty());
        }

        @Test
        @DisplayName("rotates the refresh token and rejects the consumed one")
        void rotatesRefreshToken() {
            String first = refreshTokenFrom(login(CUSTOMER_EMAIL));

            String second = refreshTokenFrom(refresh(first));
            assertThat(second).isNotEqualTo(first);

            // Dentro de la ventana de gracia, reusar el token viejo ya no cierra la sesión: emite
            // otro de la misma familia, que es lo que tolera dos pestañas o una respuesta perdida.
            String third = refreshTokenFrom(refresh(first));
            assertThat(third).isNotIn(first, second);

            assertThat(refresh(second)).hasStatusOk();
        }

        @Test
        @DisplayName("logout revokes the refresh token")
        void logoutRevokesToken() {
            String refreshToken = refreshTokenFrom(login(CUSTOMER_EMAIL));

            assertThat(mvc.post().uri("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refreshToken)))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("revokes the whole family when a rotated token is replayed after the grace window")
        void revokesFamilyOnReuse() {
            String first = refreshTokenFrom(login(CUSTOMER_EMAIL));
            String second = refreshTokenFrom(refresh(first));

            // Simula que la ventana de gracia ya pasó para el token rotado.
            refreshTokenRepository.findAll().stream()
                    .filter(token -> token.getRotatedAt() != null)
                    .forEach(token -> {
                        token.setRotatedAt(OffsetDateTime.now().minusHours(1));
                        refreshTokenRepository.save(token);
                    });

            assertThat(refresh(first))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("TOKEN_REVOKED");

            // La revocación tiene que haber quedado confirmada: el token legítimo tampoco sirve.
            assertThat(refresh(second)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(refreshTokenRepository.findAll())
                    .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
        }
    }

    @Nested
    @DisplayName("user endpoints")
    class UserEndpoints {

        @Test
        @DisplayName("lets any authenticated user read their own profile")
        void allowsSelfLookup() {
            assertThat(mvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.email").isEqualTo(CUSTOMER_EMAIL);
        }

        @Test
        @DisplayName("blocks a customer from reading another user by id")
        void blocksCustomerLookupById() {
            var other = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();

            assertThat(mvc.get().uri("/api/users/{id}", other.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("lets an admin page through the user list")
        void allowsAdminListing() {
            assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content", content -> assertThat(content).asArray().isNotNull())
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(2));
        }
    }

    @Nested
    @DisplayName("access matrix")
    class AccessMatrix {

        /**
         * Quién hace la petición; {@code NONE} es una petición anónima.
         */
        enum Caller {
            NONE(null), CUSTOMER(CUSTOMER_EMAIL), ADMIN(ADMIN_EMAIL);

            private final @Nullable String email;

            Caller(@Nullable String email) {
                this.email = email;
            }
        }

        /**
         * Una fila por regla de {@code SecurityConfig}. Sólo se verifica que la regla deje pasar o
         * corte; lo que responde el endpoint después es asunto de otras pruebas, por eso los casos
         * permitidos no usan identificadores reales.
         */
        @ParameterizedTest(name = "{0} {1} as {2} → {3}")
        @CsvSource(textBlock = """
                GET,  /api/movies,           NONE,     200
                GET,  /api/products,         NONE,     200
                GET,  /api/metadata/enums,   NONE,     200
                GET,  /api/reservations,     NONE,     401
                GET,  /api/inventory,        NONE,     401
                GET,  /api/users,            NONE,     401
                POST, /api/movies,           NONE,     401
                GET,  /api/reservations,     CUSTOMER, 200
                GET,  /api/users/me,         CUSTOMER, 200
                GET,  /api/inventory,        CUSTOMER, 403
                GET,  /api/users,            CUSTOMER, 403
                GET,  /api/reservations/all, CUSTOMER, 403
                POST, /api/movies,           CUSTOMER, 403
                GET,  /api/inventory,        ADMIN,    200
                GET,  /api/users,            ADMIN,    200
                GET,  /api/reservations/all, ADMIN,    200
                """)
        void enforcesRules(HttpMethod method, String path, Caller caller, int expectedStatus) {
            var request = mvc.method(method).uri(path);
            if (HttpMethod.POST.equals(method)) {
                request.contentType(MediaType.APPLICATION_JSON).content("{}");
            }
            if (caller.email != null) {
                request.header(HttpHeaders.AUTHORIZATION, bearer(caller.email));
            }
            assertThat(request).hasStatus(expectedStatus);
        }
    }

    @Nested
    @DisplayName("error envelope")
    class ErrorEnvelope {

        @Test
        @DisplayName("returns a problem detail body for an unauthenticated request")
        void returnsProblemDetailFor401() {
            assertThat(mvc.get().uri("/api/reservations"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo(401))
                    .hasPathSatisfying("$.detail", detail -> assertThat(detail).isNotEmpty());
        }

        @Test
        @DisplayName("returns a problem detail body for a forbidden request")
        void returnsProblemDetailFor403() {
            assertThat(mvc.get().uri("/api/inventory").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.status").isEqualTo(403);
        }

        @Test
        @DisplayName("translates an unknown sort property instead of leaking the query")
        void translatesUnknownSortProperty() {
            assertThat(mvc.get().uri("/api/movies").param("sort", "bogus,asc"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").isEqualTo("Unknown sort property 'bogus'");
        }

        @Test
        @DisplayName("refuses to sort by a property the listing does not publish")
        void refusesUnpublishedSortProperty() {
            assertThat(mvc.get().uri("/api/users").param("sort", "password")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("INVALID_SORT_PROPERTY"))
                    .hasPathSatisfying("$.context.property", property -> assertThat(property).isEqualTo("password"));
        }

        @Test
        @DisplayName("caps the page size")
        void capsPageSize() {
            assertThat(mvc.get().uri("/api/users").param("size", "5000")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.size").isEqualTo(100);
        }

        @Test
        @DisplayName("keeps the full reservation listing for administrators")
        void restrictsReservationListingToAdmins() {
            assertThat(mvc.get().uri("/api/reservations/all").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
            assertThat(mvc.get().uri("/api/reservations/all").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("wraps a malformed JSON body in a problem detail with a code")
        void returnsProblemDetailForMalformedBody() {
            assertThat(mvc.post().uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
        }

        @Test
        @DisplayName("wraps a malformed path variable in a problem detail with a code")
        void returnsProblemDetailForTypeMismatch() {
            assertThat(mvc.get().uri("/api/movies/not-a-uuid"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
        }
    }
}
