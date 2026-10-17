package tp.appsinter.cine.shared.error;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raíz de la jerarquía de errores de dominio. Es sellada para que el manejador global pueda hacer
 * un {@code switch} exhaustivo sobre ella.
 *
 * <p>Cada excepción transporta un {@link ErrorCode} estable y, opcionalmente, un mapa de contexto
 * específico del código. Ambos se publican en la respuesta RFC 7807 para que el cliente pueda
 * reaccionar sin interpretar el mensaje humano, que está localizado.
 */
@Getter
public sealed abstract class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, BusinessRuleException {

    /**
     * Clave de contexto reservada para errores por campo ({@code Map<String, String>}). El manejador
     * global la publica como la propiedad {@code errors}, igual que la validación de Bean Validation,
     * en lugar de dentro de {@code context}.
     */
    public static final String FIELD_ERRORS = "errors";

    /**
     * Clave de contexto reservada para las violaciones legibles por máquina ({@code List<Violation>})
     * que acompañan a {@link #FIELD_ERRORS}. Se publica como la propiedad {@code violations}.
     */
    public static final String VIOLATIONS = "violations";

    /**
     * Código estable y no localizado que identifica la falla.
     */
    private final ErrorCode code;
    /**
     * Datos adicionales asociados al código. Nunca es {@code null}; puede estar vacío.
     */
    private final transient Map<String, Object> context;

    protected DomainException(String message, ErrorCode code, @Nullable Map<String, Object> context) {
        super(message);
        this.code = code;
        this.context = context == null || context.isEmpty() ? Map.of() : Map.copyOf(context);
    }

    /**
     * Construye un mapa de contexto descartando las entradas cuyo valor sea {@code null}, de modo
     * que quien lanza el error pueda pasar campos opcionales sin ramificar.
     */
    protected static Map<String, Object> context(@Nullable Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Context requires an even number of key/value arguments");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (int index = 0; index < keyValuePairs.length; index += 2) {
            Object value = keyValuePairs[index + 1];
            if (value != null) {
                context.put(String.valueOf(keyValuePairs[index]), value);
            }
        }
        return context;
    }

}
