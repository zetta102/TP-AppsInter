package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.utils.ScreenRoomType;

import java.util.UUID;

public record CreateScreenRoomResponseDTO(
        UUID uuid,
        String roomName,
        ScreenRoomType screenRoomType,
        int seatCount
) {
    public static CreateScreenRoomResponseDTO from(ScreenRoom screenRoom) {
        return new CreateScreenRoomResponseDTO(
                screenRoom.getUuid(),
                screenRoom.getRoomName(),
                screenRoom.getScreenRoomType(),
                screenRoom.getSeats().size()
        );
    }
}
