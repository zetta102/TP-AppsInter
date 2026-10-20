package tp.appsinter.cine.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que un usuario puede corregir de su propia cuenta.
 *
 * <p>No incluye el correo ni el rol: el correo identifica la cuenta en los tokens ya emitidos, y el
 * rol es decisión de administración.
 */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequestDTO(
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName
) {
}
