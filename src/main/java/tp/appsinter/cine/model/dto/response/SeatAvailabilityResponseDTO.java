package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

/**
 * Butaca dentro del mapa de una función.
 *
 * <p>{@code available} es lo que decide si se puede elegir; {@code held} explica por qué no, cuando
 * el motivo es que otro cliente la está comprando en este momento y no que ya esté vendida.
 */
@Schema(name = "SeatAvailability")
public record SeatAvailabilityResponseDTO(
        UUID seatId,
        String row,
        String column,
        SeatType seatType,
        boolean available,
        boolean held
) {
    public static SeatAvailabilityResponseDTO of(Seat seat, boolean available, boolean held) {
        return new SeatAvailabilityResponseDTO(
                seat.getUuid(),
                seat.getPosition().row(),
                seat.getPosition().column(),
                seat.getSeatType(),
                available,
                held
        );
    }
}
