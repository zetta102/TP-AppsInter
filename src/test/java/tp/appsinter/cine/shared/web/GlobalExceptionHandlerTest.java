package tp.appsinter.cine.shared.web;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import tp.appsinter.cine.auth.RefreshTokenRevokedException;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.DomainException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.error.Violation;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

@DisplayName("Problem detail mapping")
class GlobalExceptionHandlerTest {

    /**
     * Una muestra de cada familia de error de dominio; cada una se informa como un caso propio.
     */
    static final List<Arguments> DOMAIN_ERRORS = List.of(
            argumentSet("not found", NotFoundException.of("Movie", UUID.randomUUID())),
            argumentSet("email taken", ConflictException.emailTaken("a@b.c")),
            argumentSet("already cancelled", BusinessRuleException.alreadyCancelled(UUID.randomUUID())),
            argumentSet("empty layout", BusinessRuleException.emptyLayout()),
            argumentSet("wrong password", BusinessRuleException.wrongPassword()));

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemFactory(ProblemProperties.defaults()));

    @ParameterizedTest
    @FieldSource("DOMAIN_ERRORS")
    @DisplayName("publishes every domain error with the status registered for its code")
    void statusComesFromErrorCode(DomainException exception) {
        ProblemDetail problem = handler.handleDomain(exception);

        assertThat(problem.getStatus()).isEqualTo(exception.getCode().status().value());
        assertThat(problem.getProperties()).containsEntry("code", exception.getCode().name());
        assertThat(problem.getType().toString())
                .startsWith(ProblemProperties.DEFAULT_TYPE_BASE)
                .endsWith(exception.getCode().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    @Test
    @DisplayName("publishes service-side field errors like bean validation errors")
    void fieldErrorsUseTheValidationEnvelope() {
        ProblemDetail problem = handler.handleDomain(
                BusinessRuleException.invalidField("startsAt", "Future", "must be a future date"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("errors", Map.of("startsAt", "must be a future date"))
                .containsEntry("violations", List.of(Violation.of("startsAt", "Future")))
                .doesNotContainKey("context");
        assertThat(problem.getType()).hasToString("https://cine.example/problems/validation-failed");
    }

    @Test
    @DisplayName("reports a replayed refresh token as revoked, not as bad credentials")
    void refreshReuseIsRevoked() {
        ProblemDetail problem = handler.handleAuthentication(new RefreshTokenRevokedException("reused"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getProperties()).containsEntry("code", "TOKEN_REVOKED");
        assertThat(problem.getDetail()).isEqualTo("reused");

        ProblemDetail rejected = handler.handleAuthentication(new BadCredentialsException("bad password"));

        assertThat(rejected.getProperties()).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(rejected.getDetail()).isEqualTo("Invalid credentials");
    }

    @ParameterizedTest(name = "{0} / {1} → {3}")
    @CsvSource(textBlock = """
            23503, fk_movie_screening_movie,          FOREIGN_KEY, RESOURCE_IN_USE,    409
            23505, uk_app_user_email_lower,           UNIQUE,      EMAIL_TAKEN,        409
            23P01, ex_movie_screening_room_overlap,   OTHER,       SCREENING_OVERLAP,  409
            23505, uk_screen_room_name_lower,         UNIQUE,      NAME_TAKEN,         409
            23505, uk_inventory_product_name_lower,   UNIQUE,      NAME_TAKEN,         409
            23505, uk_seat_room_position,             UNIQUE,      NAME_TAKEN,         409
            23505, uk_ticket_screening_seat,          UNIQUE,      SEATS_UNAVAILABLE,  409
            23514, ck_inventory_quantity,             CHECK,       INSUFFICIENT_STOCK, 422
            23505, uk_unknown,                        UNIQUE,      CONFLICT,           409
            """)
    @DisplayName("translates database constraint violations to the documented codes")
    void translatesConstraintViolations(String sqlState,
                                        String constraint,
                                        ConstraintViolationException.ConstraintKind kind,
                                        String expectedCode,
                                        int expectedStatus) {
        var cause = new ConstraintViolationException("violation", new SQLException("boom", sqlState), kind, constraint);

        ProblemDetail problem = handler.handleDataIntegrity(new DataIntegrityViolationException("wrapped", cause));

        assertThat(problem.getProperties()).containsEntry("code", expectedCode);
        assertThat(problem.getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("reports a value that does not fit its column as a bad request, not a conflict")
    void valueTooLongIsBadRequest() {
        var cause = new DataException("value too long", new SQLException("value too long for type", "22001"));

        ProblemDetail problem = handler.handleDataIntegrity(new DataIntegrityViolationException("wrapped", cause));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
    }

    @Test
    @DisplayName("treats an illegal argument that is not about sorting as an internal error")
    void illegalArgumentIsInternal() {
        ProblemDetail problem = handler.handleIllegalArgument(new IllegalArgumentException("index 7 out of bounds"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("index 7");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("never leaks the message of an unexpected exception")
    void hidesUnexpectedErrors() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("hunter2");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
