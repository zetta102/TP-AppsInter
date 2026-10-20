package tp.appsinter.cine.shared.error;

import java.util.UUID;

/**
 * El recurso no existe, o existe pero no pertenece a quien lo solicita. Ambos casos se responden
 * igual para no filtrar la existencia de datos ajenos. El nombre del recurso se publica en
 * {@code context.resource}.
 */
public final class NotFoundException extends DomainException {

    private NotFoundException(String message, String resource) {
        super(message, ErrorCode.NOT_FOUND, context("resource", resource));
    }

    /**
     * El caso habitual: una entidad buscada por su identificador.
     */
    public static NotFoundException of(String resource, UUID uuid) {
        return new NotFoundException("%s %s not found".formatted(resource, uuid), resource);
    }

    /**
     * Para cuando no hay un único identificador que nombrar, por ejemplo al pedir varias butacas.
     */
    public static NotFoundException withMessage(String resource, String message) {
        return new NotFoundException(message, resource);
    }
}
