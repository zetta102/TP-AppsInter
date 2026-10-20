package tp.appsinter.cine.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.auth.dto.request.LoginRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.dto.request.ChangePasswordRequestDTO;
import tp.appsinter.cine.user.dto.request.UpdateProfileRequestDTO;
import tp.appsinter.cine.user.dto.request.UpdateRoleRequestDTO;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.OTHER_CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.PASSWORD;

@ApiIntegrationTest
@DisplayName("Accounts")
class AccountApiTests {

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

    private UUID idOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getUuid();
    }

    private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new LoginRequestDTO(email, password)));
    }

    private MockMvcTester.MockMvcRequestBuilder changeRole(UUID userId, Role role) {
        return mvc.patch().uri("/api/users/{id}/role", userId)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new UpdateRoleRequestDTO(role)));
    }

    @Nested
    @DisplayName("own account")
    class OwnAccount {

        @Test
        @DisplayName("updates the profile and rotates the password")
        void managesOwnAccount() {
            assertThat(mvc.patch().uri("/api/users/me")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new UpdateProfileRequestDTO("Augusta", "King"))))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.firstName").isEqualTo("Augusta");

            assertThat(mvc.post().uri("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ChangePasswordRequestDTO("wrongpassword", "anothersecret", null))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("WRONG_PASSWORD");

            assertThat(mvc.post().uri("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ChangePasswordRequestDTO(PASSWORD, "anothersecret", null))))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(login(CUSTOMER, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(login(CUSTOMER, "anothersecret")).hasStatusOk();
        }
    }

    @Nested
    @DisplayName("administration")
    class Administration {

        @Test
        @DisplayName("promotes a customer but refuses to leave the cinema without admins")
        void managesRoles() {
            assertThat(changeRole(idOf(ADMIN), Role.CUSTOMER))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN");

            assertThat(changeRole(idOf(CUSTOMER), Role.ADMIN))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("refuses to delete the last admin")
        void keepsLastAdmin() {
            assertThat(mvc.delete().uri("/api/users/{id}", idOf(ADMIN))
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN");
        }

        @Test
        @DisplayName("soft-deletes a user and locks them out")
        void softDeletes() {
            assertThat(mvc.delete().uri("/api/users/{id}", idOf(OTHER_CUSTOMER))
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(login(OTHER_CUSTOMER, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(userRepository.findByEmailIgnoreCase(OTHER_CUSTOMER))
                    .hasValueSatisfying(user -> assertThat(user.getDeletedAt()).isNotNull());
        }
    }

    @Nested
    @DisplayName("user search")
    class UserSearch {

        private MockMvcTester.MockMvcRequestBuilder search(String parameter, String value) {
            return mvc.get().uri("/api/users").param(parameter, value)
                    .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN));
        }

        @Test
        @DisplayName("matches part of the e-mail or of the name, taking wildcards literally")
        void partialMatches() {
            assertThat(search("email", "ADA@"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].email", email -> assertThat(email).isEqualTo(CUSTOMER));
            assertThat(search("name", "babb"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[0].email").isEqualTo(OTHER_CUSTOMER);
            assertThat(search("name", "ada love"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[0].email").isEqualTo(CUSTOMER);
            assertThat(search("email", "%"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
        }

        @Test
        @DisplayName("lists every active account to an admin")
        void listsAccounts() {
            assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(3);
        }
    }
}
