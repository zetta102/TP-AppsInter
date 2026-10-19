package tp.appsinter.cine.contact.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tp.appsinter.cine.contact.ContactCategory;

/**
 * Contenido de un mensaje de contacto, tanto al enviarlo como al corregirlo.
 *
 * <p>No lleva nombre ni correo: el autor es siempre la cuenta autenticada.
 */
@Schema(name = "ContactMessageRequest")
public record ContactMessageRequestDTO(
        @NotNull ContactCategory category,
        @NotBlank @Size(max = 150) String subject,
        @NotBlank @Size(max = 5000) String body
) {
}
