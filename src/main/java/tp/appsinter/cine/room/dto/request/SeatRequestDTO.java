package tp.appsinter.cine.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.room.SeatType;

import java.util.UUID;

@Schema(name = "SeatRequest")
public record SeatRequestDTO(
        @NotNull UUID screenRoomId,
        @NotBlank String row,
        @NotBlank String column,
        @NotNull SeatType seatType
) {
}
