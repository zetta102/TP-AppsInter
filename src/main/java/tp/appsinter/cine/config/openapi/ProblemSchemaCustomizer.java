package tp.appsinter.cine.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.exception.ErrorCode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documenta el sobre de error común a toda la API.
 *
 * <p>Agrega los esquemas {@code ErrorCode} (el registro completo de códigos), {@code Violation} y
 * {@code Problem}, y una respuesta {@code default} en {@code application/problem+json} a cada
 * operación, de modo que un generador de tipos produzca la unión de códigos en lugar de
 * {@code string}. Reemplaza las respuestas de error que springdoc deduce de los
 * {@code @ExceptionHandler}, que no distinguen qué endpoint puede devolver qué.
 */
@Component
public class ProblemSchemaCustomizer implements GlobalOpenApiCustomizer {

    static final String PROBLEM = "Problem";
    static final String VIOLATION = "Violation";
    static final String ERROR_CODE = "ErrorCode";
    private static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private static Schema<?> typed(String type) {
        Schema<?> schema = new Schema<>();
        schema.addType(type);
        return schema;
    }

    private static Schema<?> errorCodeSchema() {
        Schema<String> schema = new Schema<>();
        schema.addType("string");
        schema.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        schema.setDescription("Stable, non-localized error code. Branch on this, never on detail.");
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static Schema<?> violationSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("field", typed("string")
                .description("Rejected field or parameter, same path as in errors"));
        properties.put("code", typed("string")
                .description("Constraint name (Size, NotBlank, HttpUrl...) or binding code (typeMismatch)"));
        properties.put("params", typed("object").additionalProperties(true)
                .description("Constraint attributes (min, max...); empty when there are none"));
        Schema<?> schema = typed("object");
        schema.setProperties(properties);
        schema.setRequired(List.of("field", "code", "params"));
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static Schema<?> problemSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("type", typed("string").format("uri")
                .description("Stable URI per code: <base>/<code-in-kebab-case>"));
        properties.put("title", typed("string"));
        properties.put("status", typed("integer").format("int32"));
        properties.put("detail", typed("string").description("Human-readable, English; do not branch on it"));
        properties.put("instance", typed("string").format("uri"));
        properties.put("code", ref(ERROR_CODE));
        properties.put("errors", typed("object").additionalProperties(typed("string"))
                .description("VALIDATION_FAILED only: first message per field"));
        properties.put("violations", typed("array").items(ref(VIOLATION))
                .description("VALIDATION_FAILED only: every violated constraint, machine-readable"));
        properties.put("context", typed("object").additionalProperties(true)
                .description("Code-specific data, e.g. seatIds for SEATS_UNAVAILABLE"));
        Schema<?> schema = typed("object");
        schema.setProperties(properties);
        schema.setRequired(List.of("type", "title", "status", "detail", "code"));
        return schema;
    }

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getComponents()
                .addSchemas(ERROR_CODE, errorCodeSchema())
                .addSchemas(VIOLATION, violationSchema())
                .addSchemas(PROBLEM, problemSchema());
        openApi.getComponents().getSchemas().remove("ProblemDetail");
        // El emisor SSE es un detalle de Spring, no un tipo del contrato: el endpoint del canal de
        // butacas documenta su contenido a mano.
        openApi.getComponents().getSchemas().remove("SseEmitter");
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::addDefaultResponse));
    }

    private void addDefaultResponse(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.keySet().removeIf(status -> !status.startsWith("2") && !status.startsWith("3"));
        responses.addApiResponse("default", new ApiResponse()
                .description("Error (RFC 7807 problem details)")
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(ref(PROBLEM)))));
    }
}
