package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.model.utils.ScreenRoomType;

public record CreateScreenRoomDTO(
        @NotBlank String roomName,
        @NotNull ScreenRoomType screenRoomType
) {
}
