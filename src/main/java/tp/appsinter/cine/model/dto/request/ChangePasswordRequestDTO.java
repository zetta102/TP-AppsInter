package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de contraseña de la propia cuenta.
 *
 * @param currentPassword contraseña vigente; se exige para que un token robado no alcance por sí
 *                        solo para quedarse con la cuenta
 * @param newPassword     contraseña nueva, con el mismo largo mínimo que en el alta
 * @param refreshToken    token de refresco de la sesión actual, opcional. Al cambiar la contraseña
 *                        se revocan todas las sesiones; indicarlo permite que la que hizo el cambio
 *                        siga viva y quien cambia su contraseña no se eche a sí mismo.
 */
public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        String refreshToken
) {
}
