package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.ScreenRoom;

import java.util.List;
import java.util.UUID;

/**
 * Seat grid for a room. {@code rows} and {@code columns} are the distinct, naturally ordered
 * coordinates so a client can render a grid without inferring the dimensions itself.
 */
public record SeatLayoutResponseDTO(
        UUID screenRoomId,
        String roomName,
        List<String> rows,
        List<String> columns,
        List<CreateSeatResponseDTO> seats
) {
    public static SeatLayoutResponseDTO of(ScreenRoom room,
                                           List<String> rows,
                                           List<String> columns,
                                           List<CreateSeatResponseDTO> seats) {
        return new SeatLayoutResponseDTO(room.getUuid(), room.getRoomName(), rows, columns, seats);
    }
}
