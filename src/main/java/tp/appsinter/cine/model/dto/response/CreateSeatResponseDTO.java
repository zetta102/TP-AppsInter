package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

@Schema(name = "Seat")
public record CreateSeatResponseDTO(
        UUID uuid,
        UUID screenRoomId,
        String row,
        String column,
        SeatType seatType
) {
    public static CreateSeatResponseDTO from(Seat seat) {
        return new CreateSeatResponseDTO(
                seat.getUuid(),
                seat.getScreenRoom().getUuid(),
                seat.getPosition().row(),
                seat.getPosition().column(),
                seat.getSeatType()
        );
    }
}
