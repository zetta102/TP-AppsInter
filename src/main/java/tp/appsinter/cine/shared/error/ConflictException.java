package tp.appsinter.cine.shared.error;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conflicto con el estado actual del recurso. Se publica como {@code 409} y, salvo indicación
 * contraria, es reintentable después de que el cliente vuelva a leer el recurso.
 */
public final class ConflictException extends DomainException {

    private ConflictException(String message, ErrorCode code, Map<String, Object> context) {
        super(message, code, context);
    }

    /**
     * Ya existe una cuenta registrada con ese correo.
     */
    public static ConflictException emailTaken(String email) {
        return new ConflictException("Email %s is already registered".formatted(email),
                ErrorCode.EMAIL_TAKEN, Map.of());
    }

    /**
     * Valor duplicado en un campo que debe ser único dentro de su ámbito.
     */
    public static ConflictException nameTaken(String message, String field, Object value) {
        return new ConflictException(message, ErrorCode.NAME_TAKEN, context("field", field, "value", value));
    }

    /**
     * Alguna posición de butaca ya está ocupada en la sala, o el propio lote la repite. El contexto
     * lleva la lista completa para que el cliente pueda corregir de una sola vez en lugar de
     * descubrir los choques de a uno.
     */
    public static ConflictException seatPositionsTaken(String message, @Nullable Collection<String> conflicts) {
        return new ConflictException(message, ErrorCode.NAME_TAKEN,
                context("conflicts", conflicts == null ? null : List.copyOf(conflicts)));
    }

    /**
     * Alguna butaca ya fue vendida o está retenida por otra persona.
     */
    public static ConflictException seatsUnavailable(String message, @Nullable Collection<UUID> seatIds) {
        return new ConflictException(message, ErrorCode.SEATS_UNAVAILABLE,
                context("seatIds", seatIds == null ? null : List.copyOf(seatIds)));
    }

    /**
     * La sala ya tiene una función que se superpone con el horario pedido.
     */
    public static ConflictException screeningOverlap(String message,
                                                     UUID conflictingScreeningId,
                                                     OffsetDateTime startsAt,
                                                     OffsetDateTime endsAt) {
        return new ConflictException(message, ErrorCode.SCREENING_OVERLAP,
                context("conflictingScreeningId", conflictingScreeningId,
                        "startsAt", startsAt,
                        "endsAt", endsAt));
    }

    /**
     * La operación está bloqueada porque otros registros dependen del recurso: borrarlo, archivarlo,
     * moverlo o reprogramarlo los dejaría inconsistentes.
     *
     * @param resource     el recurso que no puede cambiar
     * @param referencedBy lo que depende de él
     */
    public static ConflictException resourceInUse(String message, String resource, String referencedBy) {
        return new ConflictException(message, ErrorCode.RESOURCE_IN_USE,
                context("resource", resource, "referencedBy", referencedBy));
    }
}
