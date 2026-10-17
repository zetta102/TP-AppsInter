package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;

import java.util.UUID;

@Schema(name = "User")
public record CreateUserResponseDTO(
        UUID uuid,
        String firstName,
        String lastName,
        String email,
        Role role
) {
    public static CreateUserResponseDTO from(User user) {
        return new CreateUserResponseDTO(
                user.getUuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
