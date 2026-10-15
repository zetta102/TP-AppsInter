package tp.appsinter.cine.controller;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tp.appsinter.cine.exception.DomainException;
import tp.appsinter.cine.exception.ErrorCode;
import tp.appsinter.cine.security.RefreshTokenReusedException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce toda falla a un cuerpo RFC 7807 con la propiedad {@code code}.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para que también los errores del propio
 * framework —cuerpo ilegible, parámetro de tipo incorrecto, ruta o método inexistente— compartan el
 * mismo sobre que los errores de dominio, en lugar del JSON de error por defecto de Spring Boot.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Pattern UNKNOWN_ATTRIBUTE = Pattern.compile("attribute '([^']+)'");

    /**
     * Restricciones con traducción propia (ver {@code V11__integrity_hardening.sql}).
     */
    private static final String EMAIL_CONSTRAINT = "uk_app_user_email_lower";
    private static final String SCREENING_OVERLAP_CONSTRAINT = "ex_movie_screening_room_overlap";
    /**
     * SQLSTATE de Postgres para una restricción {@code EXCLUDE} violada.
     */
    private static final String EXCLUSION_VIOLATION = "23P01";

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

    /**
     * El estado HTTP sale del propio {@link ErrorCode}: es la única fuente de verdad, de modo que el
     * código publicado y el estado de la respuesta no pueden contradecirse.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException exception) {
        ProblemDetail problem = problem(exception.getMessage(), exception.getCode());
        Map<String, Object> context = new LinkedHashMap<>(exception.getContext());
        Object fieldErrors = context.remove(DomainException.FIELD_ERRORS);
        if (fieldErrors != null) {
            problem.setProperty("errors", fieldErrors);
        }
        if (!context.isEmpty()) {
            problem.setProperty("context", context);
        }
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return unknownAttribute(exception)
                .map(this::sortProblem)
                .orElseGet(() -> problem(exception.getMessage(), ErrorCode.BAD_REQUEST));
    }

    /**
     * Las escrituras concurrentes de stock pierden la comprobación de {@code @Version} al confirmar;
     * el cliente sólo tiene que reintentar la petición.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return problem("The resource was modified concurrently, please retry", ErrorCode.OPTIMISTIC_LOCK);
    }

    /**
     * Restricciones de la base que respaldan reglas que la aplicación ya comprueba antes de escribir.
     * Llegar acá significa que una petición simultánea ganó la carrera, o que se intentó borrar algo
     * que otros registros siguen referenciando; se publica el mismo código que la comprobación previa
     * habría dado.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        ConstraintViolationException violation = findCause(exception, ConstraintViolationException.class);
        if (violation == null) {
            return problem("The request conflicts with the current state of the resource", ErrorCode.CONFLICT);
        }
        String constraint = violation.getConstraintName() == null ? "" : violation.getConstraintName().toLowerCase();
        if (EXCLUSION_VIOLATION.equals(violation.getSQLState()) || constraint.equals(SCREENING_OVERLAP_CONSTRAINT)) {
            return problem("The room already has a screening overlapping that time", ErrorCode.SCREENING_OVERLAP);
        }
        if (constraint.equals(EMAIL_CONSTRAINT)) {
            return problem("Email is already registered", ErrorCode.EMAIL_TAKEN);
        }
        if (violation.getKind() == ConstraintViolationException.ConstraintKind.FOREIGN_KEY) {
            return problem("The resource is still referenced by other records", ErrorCode.RESOURCE_IN_USE);
        }
        return problem("The request conflicts with the current state of the resource", ErrorCode.CONFLICT);
    }

    private static <T extends Throwable> @Nullable T findCause(Throwable exception, Class<T> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        return sortProblem(exception.getPropertyName());
    }

    @ExceptionHandler(SortWhitelist.InvalidSortPropertyException.class)
    public ProblemDetail handleUnpublishedSortProperty(SortWhitelist.InvalidSortPropertyException exception) {
        return sortProblem(exception.getProperty());
    }

    /**
     * Un token de refresco reutilizado fuera de la ventana de gracia: la familia ya fue revocada y el
     * cliente tiene que volver a iniciar sesión en lugar de reintentar.
     */
    @ExceptionHandler(RefreshTokenReusedException.class)
    public ProblemDetail handleRefreshTokenReused(RefreshTokenReusedException exception) {
        return problem(exception.getMessage(), ErrorCode.TOKEN_REVOKED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return problem("Invalid credentials", ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem("Access denied", ErrorCode.FORBIDDEN);
    }

    /**
     * Última red: nada inesperado sale con su mensaje interno ni con el cuerpo de error por defecto.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem("An unexpected error occurred", ErrorCode.INTERNAL_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException exception,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return validationProblem(errors);
    }

    /**
     * Validación de parámetros sueltos ({@code @RequestParam}, {@code @PathVariable}) anotados con
     * restricciones de Bean Validation. Se publica igual que la de cuerpos.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.putIfAbsent(name == null ? "argument" : name, error.getDefaultMessage()));
        }
        return validationProblem(errors);
    }

    /**
     * Punto común de todos los errores del framework: completa el cuerpo que arma Spring con el
     * título y el {@code code} que publica el resto de la API.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body,
                                                          @NonNull HttpHeaders headers,
                                                          @NonNull HttpStatusCode statusCode,
                                                          @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty("code", codeFor(statusCode).name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.INVALID_CREDENTIALS;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    private ResponseEntity<Object> validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem("Request validation failed", ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private ProblemDetail sortProblem(String attribute) {
        ProblemDetail problem = problem("Unknown sort property '%s'".formatted(attribute),
                ErrorCode.INVALID_SORT_PROPERTY);
        problem.setProperty("context", Map.of("property", attribute));
        return problem;
    }

    private ProblemDetail problem(String detail, ErrorCode code) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        return problem;
    }
}
