package tp.appsinter.cine.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.user.User;

import java.util.UUID;

/**
 * Identidad del titular de una reserva.
 *
 * <p>Es una proyección mínima de {@link User}: alcanza para que la consola de administración
 * responda «¿de quién es esta reserva?» sin publicar el rol ni ningún otro dato de la cuenta.
 */
@Schema(name = "ReservationUser")
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
