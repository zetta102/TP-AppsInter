package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Petición de bloqueo temporal de butacas para una función.
 *
 * <p>Los identificadores repetidos se colapsan: pedir dos veces la misma butaca es un descuido del
 * cliente, no un intento de reservarla dos veces.
 */
public record CreateSeatHoldDTO(
        @NotEmpty List<UUID> seatIds
) {
    public CreateSeatHoldDTO {
        seatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
    }
}
