package tp.appsinter.cine.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.User;

import java.util.UUID;

@Schema(name = "User")
public record UserResponseDTO(
        UUID uuid,
        String firstName,
        String lastName,
        String email,
        Role role
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getUuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
