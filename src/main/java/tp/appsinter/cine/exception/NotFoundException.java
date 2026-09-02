package tp.appsinter.cine.exception;

import java.util.UUID;

public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, UUID uuid) {
        return new NotFoundException("%s %s not found".formatted(entity, uuid));
    }
}
