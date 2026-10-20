package tp.appsinter.cine.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.shared.validation.SeatRow;

import java.util.UUID;

@Schema(name = "SeatRequest")
public record SeatRequestDTO(
        @NotNull UUID screenRoomId,
        @NotBlank @SeatRow String row,
        @NotBlank @Size(max = BulkSeatsRequestDTO.MAX_COLUMN_LENGTH) String column,
        @NotNull SeatType seatType
) {
}
