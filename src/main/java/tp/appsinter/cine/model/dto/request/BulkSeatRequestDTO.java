package tp.appsinter.cine.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.List;

/**
 * Alta de muchas butacas en una sala, en una sola transacción.
 *
 * <p>La sala va en la ruta y no en cada butaca: repetirla doscientas veces sólo agrega ocasiones de
 * que una fila apunte a otro lado.
 */
public record BulkSeatRequestDTO(
        @NotEmpty List<@NotNull @Valid SeatDefinition> seats,
        SeatConflictPolicy onConflict
) {
    public BulkSeatRequestDTO {
        seats = seats == null ? List.of() : List.copyOf(seats);
        onConflict = onConflict == null ? SeatConflictPolicy.FAIL : onConflict;
    }

    /**
     * Una butaca dentro del lote.
     */
    public record SeatDefinition(
            @NotBlank String row,
            @NotBlank String column,
            @NotNull SeatType seatType
    ) {
    }
}
