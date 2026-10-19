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
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Contact messages")
class ContactMessageApiTests {

    private static final String AUTHOR_EMAIL = "ada@example.com";
    private static final String OTHER_EMAIL = "grace@example.com";
    private static final String ADMIN_EMAIL = "root@example.com";
    private static final String PASSWORD = "supersecret";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private UserService userService;

    private static String body(String category, String subject, String body) {
        return "{\"category\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}".formatted(category, subject, body);
    }

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncate(jdbcTemplate);

        userService.createUser(new RegisterRequestDTO("Ada", "Lovelace", AUTHOR_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new RegisterRequestDTO("Grace", "Hopper", OTHER_EMAIL, PASSWORD), Role.CUSTOMER);
        userService.createUser(new RegisterRequestDTO("Root", "Admin", ADMIN_EMAIL, PASSWORD), Role.ADMIN);
    }

    private String bearer(String email) {
        return "Bearer " + assertThat(mvc.post().uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.token").asString().actual();
    }

    private MockMvcTester.MockMvcRequestBuilder send(String email, String content) {
        return mvc.post().uri("/api/contact-messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private String create(String email, String category, String subject) {
        return assertThat(send(email, body(category, subject, "Hello there")))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.uuid").asString().actual();
    }

    private MockMvcTester.MockMvcRequestBuilder edit(String email, String uuid, String content) {
        return mvc.put().uri("/api/contact-messages/{uuid}", uuid)
                .header(HttpHeaders.AUTHORIZATION, bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private MockMvcTester.MockMvcRequestBuilder adminGet(String uri, Object... vars) {
        return mvc.get().uri(uri, vars).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL));
    }

    @Nested
    @DisplayName("author endpoints")
    class AuthorEndpoints {

        @Test
        @DisplayName("creates a message owned by the caller and points to it")
        void createsMessage() {
            assertThat(send(AUTHOR_EMAIL, body("COMPLAINT", "  Sticky floor  ", "Room 3")))
                    .hasStatus(HttpStatus.CREATED)
                    .containsHeader(HttpHeaders.LOCATION)
                    .bodyJson()
                    .hasPathSatisfying("$.author.email", email -> assertThat(email).isEqualTo(AUTHOR_EMAIL))
                    .hasPathSatisfying("$.category", category -> assertThat(category).isEqualTo("COMPLAINT"))
                    .hasPathSatisfying("$.subject", subject -> assertThat(subject).isEqualTo("Sticky floor"))
                    .hasPathSatisfying("$.readAt", readAt -> assertThat(readAt).isNull());
        }

        @Test
        @DisplayName("rejects an invalid body")
        void rejectsInvalidBody() {
            assertThat(send(AUTHOR_EMAIL, body("GENERAL", " ", "Hi")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            assertThat(send(AUTHOR_EMAIL, "{\"subject\":\"Hi\",\"body\":\"Hi\"}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            assertThat(send(AUTHOR_EMAIL, body("GENERAL", "x".repeat(151), "Hi")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("lists and shows only the caller's own messages")
        void listsOnlyOwnMessages() {
            String mine = create(AUTHOR_EMAIL, "GENERAL", "Mine");
            String theirs = create(OTHER_EMAIL, "GENERAL", "Theirs");

            assertThat(mvc.get().uri("/api/contact-messages").header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].uuid", uuid -> assertThat(uuid).isEqualTo(mine));

            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", mine)
                            .header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatusOk();
            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", theirs)
                            .header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(edit(AUTHOR_EMAIL, theirs, body("GENERAL", "Hijack", "Hijack")))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("edits a message until an admin opens it")
        void editsUntilRead() {
            String uuid = create(AUTHOR_EMAIL, "GENERAL", "Original");

            assertThat(edit(AUTHOR_EMAIL, uuid, body("SUGGESTION", "Edited", "More popcorn")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.subject", subject -> assertThat(subject).isEqualTo("Edited"))
                    .hasPathSatisfying("$.category", category -> assertThat(category).isEqualTo("SUGGESTION"))
                    .hasPathSatisfying("$.updatedAt", updatedAt -> assertThat(updatedAt).isNotNull());

            assertThat(adminGet("/api/admin/contact-messages/{uuid}", uuid))
                    .hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.readAt", readAt -> assertThat(readAt).isNotNull());

            assertThat(edit(AUTHOR_EMAIL, uuid, body("SUGGESTION", "Too late", "Too late")))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("MESSAGE_ALREADY_READ");
        }
    }

    @Nested
    @DisplayName("admin inbox")
    class AdminEndpoints {

        @Test
        @DisplayName("lists every message without marking any as read, and filters")
        void listsAndFilters() {
            create(AUTHOR_EMAIL, "COMPLAINT", "Noise");
            String opened = create(OTHER_EMAIL, "GENERAL", "Hours");
            assertThat(adminGet("/api/admin/contact-messages/{uuid}", opened)).hasStatusOk();

            assertThat(adminGet("/api/admin/contact-messages"))
                    .hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(2));
            assertThat(adminGet("/api/admin/contact-messages?read=false"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].subject", subject -> assertThat(subject).isEqualTo("Noise"));
            assertThat(adminGet("/api/admin/contact-messages?category=GENERAL"))
                    .hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.content[0].uuid", uuid -> assertThat(uuid).isEqualTo(opened));
            assertThat(adminGet("/api/admin/contact-messages?email=ADA&sort=userEmail,asc"))
                    .hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1));
        }

        @Test
        @DisplayName("deletes a message")
        void deletesMessage() {
            String uuid = create(AUTHOR_EMAIL, "GENERAL", "Bye");

            assertThat(mvc.delete().uri("/api/admin/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(adminGet("/api/admin/contact-messages/{uuid}", uuid))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("keeps customers out")
        void blocksCustomers() {
            String uuid = create(AUTHOR_EMAIL, "GENERAL", "Mine");

            assertThat(mvc.delete().uri("/api/admin/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatus(HttpStatus.FORBIDDEN);
            assertThat(mvc.delete().uri("/api/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, bearer(AUTHOR_EMAIL)))
                    .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        }
    }
}
