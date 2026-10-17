package tp.appsinter.cine.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomType;

import java.util.UUID;

@Schema(name = "Room")
public record RoomResponseDTO(
        UUID uuid,
        String roomName,
        ScreenRoomType screenRoomType,
        int seatCount,
        boolean archived
) {
    public static RoomResponseDTO from(ScreenRoom screenRoom) {
        return new RoomResponseDTO(
                screenRoom.getUuid(),
                screenRoom.getRoomName(),
                screenRoom.getScreenRoomType(),
                screenRoom.getSeats().size(),
                screenRoom.isArchived()
        );
    }
}
