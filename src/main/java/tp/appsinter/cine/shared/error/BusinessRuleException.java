package tp.appsinter.cine.shared.error;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regla de negocio incumplida: reintentar la misma petición sin cambiarla siempre vuelve a fallar,
 * a diferencia de un {@link ConflictException}. El estado sale del código: {@code 422} para las
 * reglas del dominio y {@code 400 VALIDATION_FAILED} para las de un campo que Bean Validation no
 * puede expresar ({@link #invalidField}).
 */
public final class BusinessRuleException extends DomainException {

    private BusinessRuleException(String message, ErrorCode code, Map<String, Object> context) {
        super(message, code, context);
    }

    /**
     * La función ya comenzó, por lo que no admite reservas ni retenciones nuevas.
     */
    public static BusinessRuleException screeningStarted(UUID screeningId, OffsetDateTime startsAt) {
        return new BusinessRuleException("Screening %s has already started".formatted(screeningId),
                ErrorCode.SCREENING_STARTED, context("startsAt", startsAt));
    }

    /**
     * Las butacas pedidas no pertenecen a la sala donde se proyecta la función.
     */
    public static BusinessRuleException seatsWrongRoom(UUID roomId, @Nullable Collection<UUID> seatIds) {
        return new BusinessRuleException("All seats must belong to room %s".formatted(roomId),
                ErrorCode.SEATS_WRONG_ROOM,
                context("roomId", roomId, "seatIds", seatIds == null ? null : List.copyOf(seatIds)));
    }

    /**
     * El stock disponible no cubre la cantidad pedida.
     */
    public static BusinessRuleException insufficientStock(String message,
                                                          UUID inventoryId,
                                                          int requested,
                                                          int available) {
        return new BusinessRuleException(message, ErrorCode.INSUFFICIENT_STOCK,
                context("inventoryId", inventoryId, "requested", requested, "available", available));
    }

    /**
     * La reserva ya estaba cancelada.
     */
    public static BusinessRuleException alreadyCancelled(UUID reservationId) {
        return new BusinessRuleException("Reservation %s is already cancelled".formatted(reservationId),
                ErrorCode.ALREADY_CANCELLED, Map.of());
    }

    /**
     * La grilla pedida no produjo ninguna butaca, normalmente porque {@code skip} tapó todo lo
     * generado. Crear una sala vacía en silencio sería peor que rechazarlo.
     */
    public static BusinessRuleException emptyLayout() {
        return invalidField("skip", "EmptyLayout", "must leave at least one seat in the layout");
    }

    /**
     * El mensaje de contacto ya fue leído por administración y quedó fijo.
     */
    public static BusinessRuleException messageAlreadyRead(UUID messageId) {
        return new BusinessRuleException("Contact message %s has already been read".formatted(messageId),
                ErrorCode.MESSAGE_ALREADY_READ, Map.of());
    }

    /**
     * El cambio de rol o la baja dejaría el sistema sin administradores, o un administrador intenta
     * quitarse el rol o darse de baja a sí mismo.
     */
    public static BusinessRuleException lastAdmin(String message) {
        return new BusinessRuleException(message, ErrorCode.LAST_ADMIN, Map.of());
    }

    /**
     * Un campo es inválido por una regla que depende del estado y no puede expresarse con Bean
     * Validation, por ejemplo «la fecha debe ser futura sólo si cambia». Se publica con el mismo
     * sobre que la validación de cuerpos: {@code 400 VALIDATION_FAILED} con {@code errors} y
     * {@code violations}.
     *
     * @param constraint nombre de la restricción equivalente de Bean Validation ({@code Future}…),
     *                   publicado como {@code violations[].code}
     */
    public static BusinessRuleException invalidField(String field, String constraint, String message) {
        return invalidField(field, constraint, message, Map.of());
    }

    /**
     * Como {@link #invalidField(String, String, String)}, con los parámetros de la restricción
     * (publicados en {@code violations[].params}).
     */
    public static BusinessRuleException invalidField(String field,
                                                     String constraint,
                                                     String message,
                                                     Map<String, Object> params) {
        return new BusinessRuleException("Request validation failed", ErrorCode.VALIDATION_FAILED,
                Map.of(FIELD_ERRORS, Map.of(field, message),
                        VIOLATIONS, List.of(new Violation(field, constraint, params))));
    }

    /**
     * La contraseña vigente informada al cambiarla no coincide.
     */
    public static BusinessRuleException wrongPassword() {
        return new BusinessRuleException("Current password is incorrect", ErrorCode.WRONG_PASSWORD, Map.of());
    }

    /**
     * El recurso está archivado y no admite la operación pedida.
     */
    public static BusinessRuleException resourceArchived(String resource, UUID id) {
        return new BusinessRuleException("%s %s is archived".formatted(resource, id),
                ErrorCode.RESOURCE_ARCHIVED, context("resource", resource, "id", id));
    }
}
