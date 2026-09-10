package tp.appsinter.cine.exception;

import java.util.UUID;

/**
 * El recurso no existe, o existe pero no pertenece a quien lo solicita. Ambos casos se responden
 * igual para no filtrar la existencia de datos ajenos.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        this(message, null);
    }

    /**
     * @param resource nombre del recurso ausente, publicado en {@code context.resource}
     */
    public NotFoundException(String message, String resource) {
        super(message, ErrorCode.NOT_FOUND, context("resource", resource));
    }

    /**
     * Atajo para el caso habitual de buscar una entidad por su identificador.
     */
    public static NotFoundException of(String entity, UUID uuid) {
        return new NotFoundException("%s %s not found".formatted(entity, uuid), entity);
    }
}
