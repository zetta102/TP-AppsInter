package tp.appsinter.cine.controller;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.DomainException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.exception.Violation;
import tp.appsinter.cine.security.RefreshTokenReusedException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Problem detail mapping")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemFactory(ProblemFactory.DEFAULT_TYPE_BASE));

    @Test
    @DisplayName("publishes every domain error with the status registered for its code")
    void statusComesFromErrorCode() {
        Stream.<DomainException>of(
                        NotFoundException.of("Movie", UUID.randomUUID()),
                        ConflictException.emailTaken("a@b.c"),
                        BusinessRuleException.alreadyCancelled(UUID.randomUUID()),
                        BusinessRuleException.emptyAdjustment(),
                        BusinessRuleException.emptyLayout(),
                        BusinessRuleException.wrongPassword(),
                        new BusinessRuleException("generic"))
                .forEach(exception -> {
                    ProblemDetail problem = handler.handleDomain(exception);
                    assertThat(problem.getStatus())
                            .as(exception.getCode().name())
                            .isEqualTo(exception.getCode().status().value());
                    assertThat(problem.getProperties()).containsEntry("code", exception.getCode().name());
                    assertThat(problem.getType().toString())
                            .startsWith(ProblemFactory.DEFAULT_TYPE_BASE)
                            .endsWith(exception.getCode().name().toLowerCase().replace('_', '-'));
                });
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
        ProblemDetail problem = handler.handleRefreshTokenReused(new RefreshTokenReusedException("reused"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getProperties()).containsEntry("code", "TOKEN_REVOKED");
    }

    @ParameterizedTest(name = "{0} / {1} → {3}")
    @CsvSource({
            "23503, fk_movie_screening_movie,          FOREIGN_KEY, RESOURCE_IN_USE",
            "23505, uk_app_user_email_lower,           UNIQUE,      EMAIL_TAKEN",
            "23P01, ex_movie_screening_room_overlap,   OTHER,       SCREENING_OVERLAP",
            "23505, uk_screen_room_name,               UNIQUE,      CONFLICT"
    })
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
