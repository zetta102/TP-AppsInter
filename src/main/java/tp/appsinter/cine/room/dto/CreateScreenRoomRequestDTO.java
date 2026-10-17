package tp.appsinter.cine.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.room.ScreenRoomType;

@Schema(name = "RoomRequest")
public record CreateScreenRoomRequestDTO(
        @NotBlank String roomName,
        @NotNull ScreenRoomType screenRoomType
) {
}
