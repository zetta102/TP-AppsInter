package tp.appsinter.cine.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos que un usuario puede corregir de su propia cuenta.
 *
 * <p>No incluye el correo ni el rol: el correo identifica la cuenta en los tokens ya emitidos, y el
 * rol es decisión de administración.
 */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequestDTO(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
