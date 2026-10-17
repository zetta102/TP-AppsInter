package tp.appsinter.cine.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.room.ScreenRoomType;

@Schema(name = "RoomRequest")
public record RoomRequestDTO(
        @NotBlank String roomName,
        @NotNull ScreenRoomType screenRoomType
) {
}
