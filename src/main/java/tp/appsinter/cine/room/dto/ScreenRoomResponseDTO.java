package tp.appsinter.cine.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomType;

import java.util.UUID;

@Schema(name = "Room")
public record ScreenRoomResponseDTO(
        UUID uuid,
        String roomName,
        ScreenRoomType screenRoomType,
        int seatCount,
        boolean archived
) {
    public static ScreenRoomResponseDTO from(ScreenRoom screenRoom) {
        return new ScreenRoomResponseDTO(
                screenRoom.getUuid(),
                screenRoom.getRoomName(),
                screenRoom.getScreenRoomType(),
                screenRoom.getSeats().size(),
                screenRoom.isArchived()
        );
    }
}
