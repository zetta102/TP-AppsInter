package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.User;

import java.util.UUID;

/**
 * Identidad del titular de una reserva.
 *
 * <p>Es una proyección mínima de {@link User}: alcanza para que la consola de administración
 * responda «¿de quién es esta reserva?» sin publicar el rol ni ningún otro dato de la cuenta.
 */
public record ReservationUserResponseDTO(
        UUID uuid,
        String email,
        String firstName,
        String lastName
) {
    public static ReservationUserResponseDTO from(User user) {
        return new ReservationUserResponseDTO(
                user.getUuid(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}
