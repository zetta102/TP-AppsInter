package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

public record SeatAvailabilityResponseDTO(
        UUID seatId,
        String row,
        String column,
        SeatType seatType,
        boolean available
) {
    public static SeatAvailabilityResponseDTO of(Seat seat, boolean available) {
        return new SeatAvailabilityResponseDTO(
                seat.getUuid(),
                seat.getPosition().row(),
                seat.getPosition().column(),
                seat.getSeatType(),
                available
        );
    }
}
