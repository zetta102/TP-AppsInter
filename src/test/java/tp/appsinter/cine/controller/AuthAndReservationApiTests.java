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
import org.springframework.test.web.servlet.MockMvc;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.repository.RefreshTokenRepository;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGN_UP_BODY))
                .andExpect(status().isCreated());
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"supersecret"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Nested
    @DisplayName("registration and login")
    class Registration {

        @Test
        @DisplayName("registers a customer and never returns the password")
        void registersCustomer() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIGN_UP_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"))
                    .andExpect(jsonPath("$.password").doesNotExist());

            assertThat(userRepository.findByEmailIgnoreCase(EMAIL))
                    .hasValueSatisfying(user -> assertThat(user.getPassword()).isNotEqualTo(PASSWORD));
        }

        @Test
        @DisplayName("rejects an invalid payload with a validation problem detail")
        void rejectsInvalidPayload() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName":"","lastName":"","email":"not-an-email","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        @DisplayName("rejects a duplicate email with 409")
        void rejectsDuplicateEmail() throws Exception {
            register();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIGN_UP_BODY))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("rejects bad credentials with 401")
        void rejectsBadCredentials() throws Exception {
            register();
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"grace@example.com","password":"wrongpassword"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("access control")
    class AccessControl {

        @Test
        @DisplayName("requires a token to list reservations")
        void requiresToken() throws Exception {
            mockMvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("allows a logged-in customer to list their reservations")
        void allowsAuthenticatedCustomer() throws Exception {
            register();
            String token = login();

            mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("exposes the movie catalogue anonymously but blocks writes for customers")
        void protectsCatalogueWrites() throws Exception {
            register();
            String token = login();

            mockMvc.perform(get("/api/movies")).andExpect(status().isOk());
            mockMvc.perform(post("/api/movies")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"movieName":"Alien","genres":["THRILLER"],"runtime":117}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }
}
