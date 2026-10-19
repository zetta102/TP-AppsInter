package tp.appsinter.cine.contact.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.contact.ContactCategory;
import tp.appsinter.cine.contact.ContactMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mensaje de contacto tal como lo ven su autor y la administración.
 *
 * <p>{@code readAt} le indica al autor si todavía puede corregirlo.
 */
@Schema(name = "ContactMessage")
public record ContactMessageResponseDTO(
        UUID uuid,
        ContactMessageAuthorResponseDTO author,
        ContactCategory category,
        String subject,
        String body,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime updatedAt,
        @Nullable OffsetDateTime readAt
) {
    public static ContactMessageResponseDTO from(ContactMessage message) {
        return new ContactMessageResponseDTO(
                message.getUuid(),
                ContactMessageAuthorResponseDTO.from(message.getUser()),
                message.getCategory(),
                message.getSubject(),
                message.getBody(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getReadAt()
        );
    }
}
