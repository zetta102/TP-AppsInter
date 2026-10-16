package tp.appsinter.cine.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.model.utils.ScreenRoomType;

@Schema(name = "RoomRequest")
public record CreateScreenRoomDTO(
        @NotBlank String roomName,
        @NotNull ScreenRoomType screenRoomType
) {
}
