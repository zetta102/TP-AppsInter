package tp.appsinter.cine.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.user.Role;

/**
 * Cambio de rol de otra cuenta. Sólo administración.
 */
@Schema(name = "UpdateRoleRequest")
public record UpdateRoleRequestDTO(
        @NotNull Role role
) {
}
