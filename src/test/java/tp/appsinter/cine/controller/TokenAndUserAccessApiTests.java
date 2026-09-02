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
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.repository.RefreshTokenRepository;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.UserRepository;
import tp.appsinter.cine.service.UserService;

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
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

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

            mockMvc.perform(refresh(first)).andExpect(status().isUnauthorized());
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
    }
}
