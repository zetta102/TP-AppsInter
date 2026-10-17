package tp.appsinter.cine.hold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Petición de bloqueo temporal de butacas para una función.
 *
 * <p>Los identificadores repetidos se colapsan: pedir dos veces la misma butaca es un descuido del
 * cliente, no un intento de reservarla dos veces.
 */
@Schema(name = "SeatHoldRequest")
public record CreateSeatHoldRequestDTO(
        @NotEmpty List<UUID> seatIds
) {
    public CreateSeatHoldRequestDTO {
        seatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
    }
}
