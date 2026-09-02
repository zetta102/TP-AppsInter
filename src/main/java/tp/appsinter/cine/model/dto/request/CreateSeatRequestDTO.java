package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

public record CreateSeatRequestDTO(
        @NotNull UUID screenRoomId,
        @NotBlank String row,
        @NotBlank String column,
        @NotNull SeatType seatType
) {
}
