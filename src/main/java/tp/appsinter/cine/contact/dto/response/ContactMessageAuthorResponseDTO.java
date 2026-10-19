package tp.appsinter.cine.contact.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.user.User;

import java.util.UUID;

/**
 * Identidad del autor de un mensaje: lo justo para que administración sepa a quién responder.
 */
@Schema(name = "ContactMessageAuthor")
public record ContactMessageAuthorResponseDTO(
        UUID uuid,
        String email,
        String firstName,
        String lastName
) {
    public static ContactMessageAuthorResponseDTO from(User user) {
        return new ContactMessageAuthorResponseDTO(
                user.getUuid(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}
