package tp.appsinter.cine.hold.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.hold.SeatHold;
import tp.appsinter.cine.room.Seat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bloqueo temporal de butacas visto por su dueño.
 *
 * <p>Lleva {@code expiresAt} y {@code ttlSeconds} a la vez: el primero es el dato autoritativo y el
 * segundo evita que el cliente tenga que restarle la hora local al servidor para dibujar una cuenta
 * regresiva.
 */
@Schema(name = "SeatHold")
public record SeatHoldResponseDTO(
        UUID uuid,
        UUID screeningId,
        List<UUID> seatIds,
        OffsetDateTime expiresAt,
        long ttlSeconds
) {
    public static SeatHoldResponseDTO from(SeatHold hold, OffsetDateTime now) {
        return new SeatHoldResponseDTO(
                hold.getUuid(),
                hold.getMovieScreening().getUuid(),
                hold.getSeats().stream().map(Seat::getUuid).toList(),
                hold.getExpiresAt(),
                Math.max(0, Duration.between(now, hold.getExpiresAt()).toSeconds())
        );
    }
}
