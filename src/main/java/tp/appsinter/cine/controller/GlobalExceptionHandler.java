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
import tp.appsinter.cine.exception.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern UNKNOWN_ATTRIBUTE = Pattern.compile("attribute '([^']+)'");

    /**
     * Un {@code sort} sobre una propiedad inexistente llega a Hibernate como una ruta irresoluble.
     * El mensaje original arrastra la sentencia JPQL completa, así que se traduce en lugar de
     * devolverlo tal cual.
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
        ProblemDetail problem = problem(status, exception.getMessage(), exception.getCode());
        if (!exception.getContext().isEmpty()) {
            problem.setProperty("context", exception.getContext());
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return unknownAttribute(exception)
                .map(this::sortProblem)
                .orElseGet(() -> problem(HttpStatus.BAD_REQUEST, exception.getMessage(), ErrorCode.BAD_REQUEST));
    }

    /**
     * Las escrituras concurrentes de stock pierden la comprobación de {@code @Version} al confirmar;
     * el cliente sólo tiene que reintentar la petición.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "The resource was modified concurrently, please retry",
                ErrorCode.OPTIMISTIC_LOCK);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource",
                ErrorCode.CONFLICT);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        return sortProblem(exception.getPropertyName());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ErrorCode.FORBIDDEN);
    }

    private ProblemDetail sortProblem(String attribute) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Unknown sort property '%s'".formatted(attribute), ErrorCode.INVALID_SORT_PROPERTY);
        problem.setProperty("context", Map.of("property", attribute));
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        return problem;
    }
}
