package tp.appsinter.cine.controller;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.repository.RefreshTokenRepository;
import tp.appsinter.cine.repository.UserRepository;
import tp.appsinter.cine.service.UserService;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;
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
                new CreateUserRequestDTO("Linus", "Torvalds", CUSTOMER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(
                new CreateUserRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);
    }

    private String bearer(String email) throws Exception {
        String body = mockMvc.perform(login(email)).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String refreshTokenFrom(
            org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String email) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    }

    private org.springframework.test.web.servlet.RequestBuilder refresh(String refreshToken) {
        return post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken));
    }

    @Nested
    @DisplayName("refresh tokens")
    class RefreshTokens {

        @Test
        @DisplayName("login returns both an access token and a refresh token")
        void loginReturnsBothTokens() throws Exception {
            mockMvc.perform(login(CUSTOMER_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshExpiresAt").isNotEmpty());
        }

        @Test
        @DisplayName("rotates the refresh token and rejects the consumed one")
        void rotatesRefreshToken() throws Exception {
            String first = refreshTokenFrom(login(CUSTOMER_EMAIL));

            String second = refreshTokenFrom(refresh(first));
            assertThat(second).isNotEqualTo(first);

            // Dentro de la ventana de gracia, reusar el token viejo ya no cierra la sesión: emite
            // otro de la misma familia, que es lo que tolera dos pestañas o una respuesta perdida.
            String third = refreshTokenFrom(refresh(first));
            assertThat(third).isNotIn(first, second);

            mockMvc.perform(refresh(second)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("logout revokes the refresh token")
        void logoutRevokesToken() throws Exception {
            String refreshToken = refreshTokenFrom(login(CUSTOMER_EMAIL));

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refreshToken)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("revokes the whole family when a rotated token is replayed after the grace window")
        void revokesFamilyOnReuse() throws Exception {
            String first = refreshTokenFrom(login(CUSTOMER_EMAIL));
            String second = refreshTokenFrom(refresh(first));

            // Simula que la ventana de gracia ya pasó para el token rotado.
            refreshTokenRepository.findAll().stream()
                    .filter(token -> token.getRotatedAt() != null)
                    .forEach(token -> {
                        token.setRotatedAt(OffsetDateTime.now().minusHours(1));
                        refreshTokenRepository.save(token);
                    });

            mockMvc.perform(refresh(first))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

            // La revocación tiene que haber quedado confirmada: el token legítimo tampoco sirve.
            mockMvc.perform(refresh(second))
                    .andExpect(status().isUnauthorized());
            assertThat(refreshTokenRepository.findAll())
                    .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
        }
    }

    @Nested
    @DisplayName("user endpoints")
    class UserEndpoints {

        @Test
        @DisplayName("lets any authenticated user read their own profile")
        void allowsSelfLookup() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(CUSTOMER_EMAIL));
        }

        @Test
        @DisplayName("blocks a customer from reading another user by id")
        void blocksCustomerLookupById() throws Exception {
            var other = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();

            mockMvc.perform(get("/api/users/" + other.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("lets an admin page through the user list")
        void allowsAdminListing() throws Exception {
            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    @Nested
    @DisplayName("access matrix")
    class AccessMatrix {

        /**
         * Una fila por regla de {@code SecurityConfig}. El rol {@code NONE} es una petición anónima.
         * Sólo se verifica que la regla deje pasar o corte; lo que responde el endpoint después es
         * asunto de otras pruebas, por eso los casos permitidos no usan identificadores reales.
         */
        @ParameterizedTest(name = "{0} {1} as {2} → {3}")
        @CsvSource({
                "GET,  /api/movies,           NONE,     200",
                "GET,  /api/products,         NONE,     200",
                "GET,  /api/metadata/enums,   NONE,     200",
                "GET,  /api/reservations,     NONE,     401",
                "GET,  /api/inventory,        NONE,     401",
                "GET,  /api/users,            NONE,     401",
                "POST, /api/movies,           NONE,     401",
                "GET,  /api/reservations,     CUSTOMER, 200",
                "GET,  /api/users/me,         CUSTOMER, 200",
                "GET,  /api/inventory,        CUSTOMER, 403",
                "GET,  /api/users,            CUSTOMER, 403",
                "GET,  /api/reservations/all, CUSTOMER, 403",
                "POST, /api/movies,           CUSTOMER, 403",
                "GET,  /api/inventory,        ADMIN,    200",
                "GET,  /api/users,            ADMIN,    200",
                "GET,  /api/reservations/all, ADMIN,    200"
        })
        void enforcesRules(String method, String path, String role, int expectedStatus) throws Exception {
            var request = "POST".equals(method)
                    ? post(path).contentType(MediaType.APPLICATION_JSON).content("{}")
                    : get(path);
            switch (role) {
                case "CUSTOMER" -> request.header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL));
                case "ADMIN" -> request.header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL));
                default -> {
                }
            }
            mockMvc.perform(request).andExpect(status().is(expectedStatus));
        }
    }

    @Nested
    @DisplayName("error envelope")
    class ErrorEnvelope {

        @Test
        @DisplayName("returns a problem detail body for an unauthenticated request")
        void returnsProblemDetailFor401() throws Exception {
            mockMvc.perform(get("/api/reservations"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }

        @Test
        @DisplayName("returns a problem detail body for a forbidden request")
        void returnsProblemDetailFor403() throws Exception {
            mockMvc.perform(get("/api/inventory").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("translates an unknown sort property instead of leaking the query")
        void translatesUnknownSortProperty() throws Exception {
            mockMvc.perform(get("/api/movies").param("sort", "bogus,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Unknown sort property 'bogus'"));
        }

        @Test
        @DisplayName("refuses to sort by a property the listing does not publish")
        void refusesUnpublishedSortProperty() throws Exception {
            mockMvc.perform(get("/api/users").param("sort", "password")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"))
                    .andExpect(jsonPath("$.context.property").value("password"));
        }

        @Test
        @DisplayName("caps the page size")
        void capsPageSize() throws Exception {
            mockMvc.perform(get("/api/users").param("size", "5000")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("keeps the full reservation listing for administrators")
        void restrictsReservationListingToAdmins() throws Exception {
            mockMvc.perform(get("/api/reservations/all").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(get("/api/reservations/all").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("wraps a malformed JSON body in a problem detail with a code")
        void returnsProblemDetailForMalformedBody() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("wraps a malformed path variable in a problem detail with a code")
        void returnsProblemDetailForTypeMismatch() throws Exception {
            mockMvc.perform(get("/api/movies/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }
}
