package tp.appsinter.cine.shared.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import tp.appsinter.cine.auth.RefreshTokenReusedException;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.DomainException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.error.Violation;

import java.sql.SQLException;
import java.util.List;
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
            argumentSet("empty adjustment", BusinessRuleException.emptyAdjustment()),
            argumentSet("empty layout", BusinessRuleException.emptyLayout()),
            argumentSet("wrong password", BusinessRuleException.wrongPassword()),
            argumentSet("generic business rule", new BusinessRuleException("generic")));

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemFactory(ProblemFactory.DEFAULT_TYPE_BASE));

    @ParameterizedTest
    @FieldSource("DOMAIN_ERRORS")
    @DisplayName("publishes every domain error with the status registered for its code")
    void statusComesFromErrorCode(DomainException exception) {
        ProblemDetail problem = handler.handleDomain(exception);

        assertThat(problem.getStatus()).isEqualTo(exception.getCode().status().value());
        assertThat(problem.getProperties()).containsEntry("code", exception.getCode().name());
        assertThat(problem.getType().toString())
                .startsWith(ProblemFactory.DEFAULT_TYPE_BASE)
                .endsWith(exception.getCode().name().toLowerCase().replace('_', '-'));
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
        ProblemDetail problem = handler.handleAuthentication(new RefreshTokenReusedException("reused"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getProperties()).containsEntry("code", "TOKEN_REVOKED");
        assertThat(problem.getDetail()).isEqualTo("reused");

        ProblemDetail rejected = handler.handleAuthentication(new BadCredentialsException("bad password"));

        assertThat(rejected.getProperties()).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(rejected.getDetail()).isEqualTo("Invalid credentials");
    }

    @ParameterizedTest(name = "{0} / {1} → {3}")
    @CsvSource(textBlock = """
            23503, fk_movie_screening_movie,          FOREIGN_KEY, RESOURCE_IN_USE
            23505, uk_app_user_email_lower,           UNIQUE,      EMAIL_TAKEN
            23P01, ex_movie_screening_room_overlap,   OTHER,       SCREENING_OVERLAP
            23505, uk_screen_room_name,               UNIQUE,      CONFLICT
            """)
    @DisplayName("translates database constraint violations to the documented codes")
    void translatesConstraintViolations(String sqlState,
                                        String constraint,
                                        ConstraintViolationException.ConstraintKind kind,
                                        String expectedCode) {
        var cause = new ConstraintViolationException("violation", new SQLException("boom", sqlState), kind, constraint);

        ProblemDetail problem = handler.handleDataIntegrity(new DataIntegrityViolationException("wrapped", cause));

        assertThat(problem.getProperties()).containsEntry("code", expectedCode);
        assertThat(problem.getStatus()).isEqualTo(409);
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
