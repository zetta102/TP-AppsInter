package tp.appsinter.cine.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.DomainException;
import tp.appsinter.cine.exception.NotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern UNKNOWN_ATTRIBUTE = Pattern.compile("attribute '([^']+)'");

    /**
     * A {@code sort} on a property that does not exist reaches Hibernate as an unresolvable path.
     * The raw message carries the whole JPQL statement, so it is translated instead of echoed.
     */
    private static Optional<String> unknownAttribute(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            Matcher matcher = UNKNOWN_ATTRIBUTE.matcher(message);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException exception) {
        HttpStatus status = switch (exception) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case BusinessRuleException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return unknownAttribute(exception)
                .map(attribute -> problem(HttpStatus.BAD_REQUEST, "Unknown sort property '%s'".formatted(attribute)))
                .orElseGet(() -> problem(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    /**
     * Concurrent stock updates lose the {@code @Version} check on commit; the client should simply
     * retry the request.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "The resource was modified concurrently, please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Unknown sort property '%s'".formatted(exception.getPropertyName()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
