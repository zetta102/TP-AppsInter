package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.utils.ScreenRoomType;

import java.util.UUID;

@Schema(name = "Room")
public record CreateScreenRoomResponseDTO(
        UUID uuid,
        String roomName,
        ScreenRoomType screenRoomType,
        int seatCount,
        boolean archived
) {
    public static CreateScreenRoomResponseDTO from(ScreenRoom screenRoom) {
        return new CreateScreenRoomResponseDTO(
                screenRoom.getUuid(),
                screenRoom.getRoomName(),
                screenRoom.getScreenRoomType(),
                screenRoom.getSeats().size(),
                screenRoom.isArchived()
        );
    }
}
