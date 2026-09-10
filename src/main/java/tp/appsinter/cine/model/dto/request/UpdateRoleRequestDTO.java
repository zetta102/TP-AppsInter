package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.model.utils.Role;

/**
 * Cambio de rol de otra cuenta. Sólo administración.
 */
public record UpdateRoleRequestDTO(
        @NotNull Role role
) {
}
