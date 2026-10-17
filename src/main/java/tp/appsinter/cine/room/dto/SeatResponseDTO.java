package tp.appsinter.cine.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatType;

import java.util.UUID;

@Schema(name = "Seat")
public record SeatResponseDTO(
        UUID uuid,
        UUID screenRoomId,
        String row,
        String column,
        SeatType seatType
) {
    public static SeatResponseDTO from(Seat seat) {
        return new SeatResponseDTO(
                seat.getUuid(),
                seat.getScreenRoom().getUuid(),
                seat.getPosition().row(),
                seat.getPosition().column(),
                seat.getSeatType()
        );
    }
}
