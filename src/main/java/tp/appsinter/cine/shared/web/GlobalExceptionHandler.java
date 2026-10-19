package tp.appsinter.cine.shared.web;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tp.appsinter.cine.shared.error.DomainException;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.error.HasErrorCode;
import tp.appsinter.cine.shared.error.Violation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Atributos de toda restricción que no describen la regla en sí y por lo tanto no se publican
     * en {@code violations[].params}.
     */
    private static final Set<String> NON_PARAMETER_ATTRIBUTES = Set.of("message", "groups", "payload", "flags");

    private static final Pattern UNKNOWN_ATTRIBUTE = Pattern.compile("attribute '([^']+)'");

    /**
     * Restricciones con traducción propia (ver {@code V1__schema.sql}).
     */
    private static final String EMAIL_CONSTRAINT = "uk_app_user_email_lower";
    private static final String SCREENING_OVERLAP_CONSTRAINT = "ex_movie_screening_room_overlap";
    /**
     * SQLSTATE de Postgres para una restricción {@code EXCLUDE} violada.
     */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private final ProblemFactory problems;

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
        Object violations = context.remove(DomainException.VIOLATIONS);
        if (violations != null) {
            problem.setProperty("violations", violations);
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
     * Las escrituras concurrentes de stock pierden la comprobación de {@code @Version} al confirmar.
     * Las compras y cancelaciones ya se reintentan del lado del servidor ({@code @Retryable}), así que
     * esto sólo llega cuando los reintentos se agotaron o la escritura no es reintentable (la edición
     * completa de un producto); el cliente sólo tiene que repetir la petición.
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
        String constraintName = violation.getConstraintName();
        String constraint = constraintName == null ? "" : constraintName.toLowerCase(Locale.ROOT);
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
     * Credenciales rechazadas. Si la excepción trae su propio código —un token de refresco reutilizado
     * fuera de la ventana de gracia publica {@code TOKEN_REVOKED}, para que el cliente vuelva a iniciar
     * sesión en lugar de reintentar—, se publica ese código con su mensaje.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        if (exception instanceof HasErrorCode coded) {
            return problem(exception.getMessage(), coded.getCode());
        }
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
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ValidationErrors errors = new ValidationErrors();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.add(error.getField(), error, null));
        return validationProblem(errors);
    }

    /**
     * Validación de parámetros sueltos ({@code @RequestParam}, {@code @PathVariable}) anotados con
     * restricciones de Bean Validation. Se publica igual que la de cuerpos.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ValidationErrors errors = new ValidationErrors();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            String field = name == null ? "argument" : name;
            result.getResolvableErrors().forEach(error -> errors.add(field, error, result));
        }
        return validationProblem(errors);
    }

    /**
     * Punto común de todos los errores del framework: completa el cuerpo que arma Spring con el
     * {@code type}, el título y el {@code code} que publica el resto de la API.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body,
                                                          HttpHeaders headers,
                                                          HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problems.decorate(problem, existingCode(problem).orElseGet(() -> codeFor(statusCode)));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static Optional<ErrorCode> existingCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !(properties.get("code") instanceof String name)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ErrorCode.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
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

    private ResponseEntity<Object> validationProblem(ValidationErrors errors) {
        ProblemDetail problem = problem("Request validation failed", ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors.messages);
        problem.setProperty("violations", errors.violations);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private ProblemDetail sortProblem(String attribute) {
        ProblemDetail problem = problem("Unknown sort property '%s'".formatted(attribute),
                ErrorCode.INVALID_SORT_PROPERTY);
        problem.setProperty("context", Map.of("property", attribute));
        return problem;
    }

    private ProblemDetail problem(@Nullable String detail, ErrorCode code) {
        return problems.create(code, detail);
    }

    /**
     * Acumula los errores de validación en las dos formas publicadas: {@code errors} (primer mensaje
     * por campo, en inglés) y {@code violations} (todas las restricciones, legibles por máquina).
     */
    private static final class ValidationErrors {

        private final Map<String, String> messages = new LinkedHashMap<>();
        private final List<Violation> violations = new ArrayList<>();

        void add(String field, MessageSourceResolvable error, @Nullable ParameterValidationResult source) {
            String message = error.getDefaultMessage();
            messages.putIfAbsent(field, message == null ? "is invalid" : message);
            violations.add(new Violation(field, constraintName(error), constraintParams(error, source)));
        }

        /**
         * El último código de un error de Spring es el más genérico: el nombre simple de la
         * restricción ({@code Size}) o del fallo de enlace ({@code typeMismatch}).
         */
        private static String constraintName(MessageSourceResolvable error) {
            String[] codes = error.getCodes();
            return codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
        }

        private static Map<String, Object> constraintParams(MessageSourceResolvable error,
                                                            @Nullable ParameterValidationResult source) {
            ConstraintViolation<?> violation = unwrapViolation(error, source);
            if (violation == null) {
                return Map.of();
            }
            Map<String, Object> params = new LinkedHashMap<>();
            violation.getConstraintDescriptor().getAttributes().forEach((name, value) -> {
                if (!NON_PARAMETER_ATTRIBUTES.contains(name) && isPublishable(value)) {
                    params.put(name, value);
                }
            });
            return params;
        }

        private static @Nullable ConstraintViolation<?> unwrapViolation(MessageSourceResolvable error,
                                                                 @Nullable ParameterValidationResult source) {
            try {
                if (error instanceof FieldError fieldError) {
                    return fieldError.contains(ConstraintViolation.class)
                            ? fieldError.unwrap(ConstraintViolation.class)
                            : null;
                }
                return source == null ? null : source.unwrap(error, ConstraintViolation.class);
            } catch (IllegalArgumentException notAViolation) {
                return null;
            }
        }

        /**
         * Sólo valores simples: una clase o una anotación anidada no significan nada para el cliente.
         */
        private static boolean isPublishable(@Nullable Object value) {
            return value instanceof Number || value instanceof Boolean || value instanceof CharSequence
                    || value instanceof Enum<?>;
        }
    }
}
