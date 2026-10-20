package tp.appsinter.cine.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.auth.dto.request.LoginRequestDTO;
import tp.appsinter.cine.auth.dto.request.RefreshRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.MutableClock;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserRepository;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.PASSWORD;

@ApiIntegrationTest
@DisplayName("Authentication")
class AuthApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private MutableClock clock;

    private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new LoginRequestDTO(email, password)));
    }

    @Nested
    @DisplayName("registration and login")
    class Registration {

        private static final String EMAIL = "grace@example.com";

        private MockMvcTester.MockMvcRequestBuilder signUp() {
            return mvc.post().uri("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(api.json(new RegisterRequestDTO("Grace", "Hopper", EMAIL, PASSWORD)));
        }

        @Test
        @DisplayName("registers a customer and never returns the password")
        void registersCustomer() {
            assertThat(signUp())
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPathSatisfying("$.email", email -> assertThat(email).isEqualTo(EMAIL))
                    .hasPathSatisfying("$.role", role -> assertThat(role).isEqualTo("CUSTOMER"))
                    .doesNotHavePath("$.password");

            assertThat(userRepository.findByEmailIgnoreCase(EMAIL))
                    .hasValueSatisfying(user -> assertThat(user.getPassword()).isNotEqualTo(PASSWORD));
        }

        @Test
        @DisplayName("rejects an invalid payload with a validation problem detail")
        void rejectsInvalidPayload() {
            assertThat(mvc.post().uri("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"","lastName":"","email":"not-an-email","password":"short"}
                                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPath("$.errors.email")
                    .hasPath("$.errors.password");
        }

        @Test
        @DisplayName("rejects a duplicate email regardless of case")
        void rejectsDuplicateEmail() {
            assertThat(signUp()).hasStatus(HttpStatus.CREATED);

            assertThat(mvc.post().uri("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new RegisterRequestDTO("Grace", "Hopper", EMAIL.toUpperCase(Locale.ROOT), PASSWORD))))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_TAKEN");
        }

        @Test
        @DisplayName("rejects bad credentials with 401")
        void rejectsBadCredentials() {
            assertThat(signUp()).hasStatus(HttpStatus.CREATED);

            assertThat(login(EMAIL, "wrongpassword"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
        }
    }

    @Nested
    @DisplayName("refresh tokens")
    class RefreshTokens {

        @BeforeEach
        void setUp() {
            TestUsers.createAll(userService);
        }

        private MockMvcTester.MockMvcRequestBuilder refresh(String refreshToken) {
            return mvc.post().uri("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(api.json(new RefreshRequestDTO(refreshToken)));
        }

        private String refreshTokenFrom(MockMvcTester.MockMvcRequestBuilder request) {
            return assertThat(request).hasStatusOk()
                    .bodyJson().extractingPath("$.refreshToken").asString().actual();
        }

        @Test
        @DisplayName("login returns both an access token and a refresh token")
        void loginReturnsBothTokens() {
            assertThat(login(CUSTOMER, PASSWORD))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.token", token -> assertThat(token).isNotEmpty())
                    .hasPathSatisfying("$.tokenType", type -> assertThat(type).isEqualTo("Bearer"))
                    .hasPathSatisfying("$.refreshToken", token -> assertThat(token).isNotEmpty())
                    .hasPathSatisfying("$.refreshExpiresAt", expiry -> assertThat(expiry).isNotEmpty());
        }

        @Test
        @DisplayName("rotates the refresh token and tolerates a replay within the grace window")
        void rotatesRefreshToken() {
            String first = refreshTokenFrom(login(CUSTOMER, PASSWORD));

            String second = refreshTokenFrom(refresh(first));
            assertThat(second).isNotEqualTo(first);

            // Dentro de la ventana de gracia, reusar el token viejo no cierra la sesión: emite otro de
            // la misma familia, que es lo que tolera dos pestañas o una respuesta perdida.
            String third = refreshTokenFrom(refresh(first));
            assertThat(third).isNotIn(first, second);

            assertThat(refresh(second)).hasStatusOk();
        }

        @Test
        @DisplayName("revokes the whole family when a rotated token is replayed after the grace window")
        void revokesFamilyOnReuse() {
            String first = refreshTokenFrom(login(CUSTOMER, PASSWORD));
            String second = refreshTokenFrom(refresh(first));

            clock.advance(Duration.ofHours(1));

            assertThat(refresh(first))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("TOKEN_REVOKED"))
                    .hasPathSatisfying("$.detail",
                            detail -> assertThat(detail).isEqualTo("Refresh token has already been used"));

            // La revocación tiene que haber quedado confirmada: el token legítimo tampoco sirve.
            assertThat(refresh(second))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("TOKEN_REVOKED");
            assertThat(refreshTokenRepository.findAll())
                    .isNotEmpty()
                    .allSatisfy(token -> assertThat(token.getRevokedReason()).isEqualTo(RevocationReason.REUSE));
        }

        @Test
        @DisplayName("treats a refresh after logout as an ended session, not as theft")
        void logoutRevokesToken() {
            String refreshToken = refreshTokenFrom(login(CUSTOMER, PASSWORD));

            assertThat(mvc.post().uri("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new RefreshRequestDTO(refreshToken))))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(refresh(refreshToken))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("TOKEN_REVOKED"))
                    .hasPathSatisfying("$.detail", detail -> assertThat(detail).isEqualTo("Refresh token was revoked"));
            assertThat(refreshTokenRepository.findAll())
                    .isNotEmpty()
                    .allSatisfy(token -> assertThat(token.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT));
        }
    }
}
