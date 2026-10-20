package tp.appsinter.cine.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.contact.dto.request.ContactMessageRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.OTHER_CUSTOMER;

@ApiIntegrationTest
@DisplayName("Contact messages")
class ContactMessageApiTests {

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

    private static ContactMessageRequestDTO message(ContactCategory category, String subject, String body) {
        return new ContactMessageRequestDTO(category, subject, body);
    }

    private MockMvcTester.MockMvcRequestBuilder send(String email, String content) {
        return mvc.post().uri("/api/contact-messages")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private MockMvcTester.MockMvcRequestBuilder send(String email, ContactMessageRequestDTO request) {
        return send(email, api.json(request));
    }

    private String create(String email, ContactCategory category, String subject) {
        return assertThat(send(email, message(category, subject, "Hello there")))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.uuid").asString().actual();
    }

    private MockMvcTester.MockMvcRequestBuilder edit(String email, String uuid, ContactMessageRequestDTO request) {
        return mvc.put().uri("/api/contact-messages/{uuid}", uuid)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(request));
    }

    private MockMvcTester.MockMvcRequestBuilder adminGet(String uri, Object... vars) {
        return mvc.get().uri(uri, vars).header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN));
    }

    @Nested
    @DisplayName("author endpoints")
    class AuthorEndpoints {

        @Test
        @DisplayName("creates a message owned by the caller and points to it")
        void createsMessage() {
            assertThat(send(CUSTOMER, message(ContactCategory.COMPLAINT, "  Sticky floor  ", "Room \"3\"")))
                    .hasStatus(HttpStatus.CREATED)
                    .containsHeader(HttpHeaders.LOCATION)
                    .bodyJson()
                    .hasPathSatisfying("$.author.email", email -> assertThat(email).isEqualTo(CUSTOMER))
                    .hasPathSatisfying("$.category", category -> assertThat(category).isEqualTo("COMPLAINT"))
                    .hasPathSatisfying("$.subject", subject -> assertThat(subject).isEqualTo("Sticky floor"))
                    .hasPathSatisfying("$.body", body -> assertThat(body).isEqualTo("Room \"3\""))
                    .hasPathSatisfying("$.readAt", readAt -> assertThat(readAt).isNull());
        }

        @Test
        @DisplayName("rejects an invalid body")
        void rejectsInvalidBody() {
            assertThat(send(CUSTOMER, message(ContactCategory.GENERAL, " ", "Hi")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            assertThat(send(CUSTOMER, "{\"subject\":\"Hi\",\"body\":\"Hi\"}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            assertThat(send(CUSTOMER, message(ContactCategory.GENERAL, "x".repeat(151), "Hi")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("lists and shows only the caller's own messages")
        void listsOnlyOwnMessages() {
            String mine = create(CUSTOMER, ContactCategory.GENERAL, "Mine");
            String theirs = create(OTHER_CUSTOMER, ContactCategory.GENERAL, "Theirs");

            assertThat(mvc.get().uri("/api/contact-messages").header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].uuid", uuid -> assertThat(uuid).isEqualTo(mine));

            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", mine)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatusOk();
            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", theirs)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(edit(CUSTOMER, theirs, message(ContactCategory.GENERAL, "Hijack", "Hijack")))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("edits a message until an admin opens it")
        void editsUntilRead() {
            String uuid = create(CUSTOMER, ContactCategory.GENERAL, "Original");

            assertThat(edit(CUSTOMER, uuid, message(ContactCategory.SUGGESTION, "Edited", "More popcorn")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.subject", subject -> assertThat(subject).isEqualTo("Edited"))
                    .hasPathSatisfying("$.category", category -> assertThat(category).isEqualTo("SUGGESTION"))
                    .hasPathSatisfying("$.updatedAt", updatedAt -> assertThat(updatedAt).isNotNull());

            assertThat(adminGet("/api/admin/contact-messages/{uuid}", uuid))
                    .hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.readAt", readAt -> assertThat(readAt).isNotNull());

            assertThat(edit(CUSTOMER, uuid, message(ContactCategory.SUGGESTION, "Too late", "Too late")))
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
            create(CUSTOMER, ContactCategory.COMPLAINT, "Noise");
            String opened = create(OTHER_CUSTOMER, ContactCategory.GENERAL, "Hours");
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
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.sort", sort -> assertThat(sort).isEqualTo("userEmail: ASC"));
        }

        @Test
        @DisplayName("deletes a message")
        void deletesMessage() {
            String uuid = create(CUSTOMER, ContactCategory.GENERAL, "Bye");

            assertThat(mvc.delete().uri("/api/admin/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(adminGet("/api/admin/contact-messages/{uuid}", uuid))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/api/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("keeps customers out")
        void blocksCustomers() {
            String uuid = create(CUSTOMER, ContactCategory.GENERAL, "Mine");

            assertThat(mvc.delete().uri("/api/admin/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.FORBIDDEN);
            assertThat(mvc.delete().uri("/api/contact-messages/{uuid}", uuid)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        }
    }
}
