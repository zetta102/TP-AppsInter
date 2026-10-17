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
import tp.appsinter.cine.DatabaseCleaner;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Authentication and reservation endpoints")
class AuthAndReservationApiTests {

    private static final String EMAIL = "grace@example.com";
    private static final String PASSWORD = "supersecret";
    private static final String SIGN_UP_BODY = """
            {"firstName":"Grace","lastName":"Hopper","email":"grace@example.com","password":"supersecret"}
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncate(jdbcTemplate);
    }

    private MockMvcTester.MockMvcRequestBuilder signUp() {
        return mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY);
    }

    private void register() {
        assertThat(signUp()).hasStatus(HttpStatus.CREATED);
    }

    private String login() {
        return assertThat(mvc.post().uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"supersecret"}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.token").asString().actual();
    }

    @Nested
    @DisplayName("registration and login")
    class Registration {

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
                    .hasPath("$.errors.email");
        }

        @Test
        @DisplayName("rejects a duplicate email with 409")
        void rejectsDuplicateEmail() {
            register();
            assertThat(signUp()).hasStatus(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("rejects bad credentials with 401")
        void rejectsBadCredentials() {
            register();
            assertThat(mvc.post().uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"grace@example.com","password":"wrongpassword"}
                                    """))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("access control")
    class AccessControl {

        @Test
        @DisplayName("requires a token to list reservations")
        void requiresToken() {
            assertThat(mvc.get().uri("/api/reservations")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("allows a logged-in customer to list their reservations")
        void allowsAuthenticatedCustomer() {
            register();
            String token = login();

            assertThat(mvc.get().uri("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.content").asArray();
        }

        @Test
        @DisplayName("exposes the movie catalogue anonymously but blocks writes for customers")
        void protectsCatalogueWrites() {
            register();
            String token = login();

            assertThat(mvc.get().uri("/api/movies")).hasStatusOk();
            assertThat(mvc.post().uri("/api/movies")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieName":"Alien","genres":["THRILLER"],"runtime":117}
                                    """))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
