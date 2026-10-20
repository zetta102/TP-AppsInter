package tp.appsinter.cine.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.shared.validation.SeatRow;

import java.util.List;

/**
 * Alta de muchas butacas en una sala, en una sola transacción.
 *
 * <p>La sala va en la ruta y no en cada butaca: repetirla doscientas veces sólo agrega ocasiones de
 * que una fila apunte a otro lado. El lote admite como mucho una grilla del generador completa.
 */
@Schema(name = "BulkSeatsRequest")
public record BulkSeatsRequestDTO(
        @NotEmpty @Size(max = MAX_SEATS) List<@NotNull @Valid SeatDefinition> seats,
        @Nullable SeatConflictPolicy onConflict
) {

    public static final int MAX_SEATS = GenerateSeatsRequestDTO.MAX_ROWS * GenerateSeatsRequestDTO.MAX_COLUMNS;

    /**
     * Largo máximo del nombre de columna. Las columnas que numera el generador tienen a lo sumo tres
     * cifras; el margen deja lugar a nombres propios cortos.
     */
    public static final int MAX_COLUMN_LENGTH = 10;

    /**
     * La lista se copia con {@code stream().toList()} y no con {@code List.copyOf}, que rechazaría
     * un elemento nulo al leer el cuerpo en lugar de dejar que Bean Validation lo informe.
     */
    public BulkSeatsRequestDTO {
        seats = seats == null ? List.of() : seats.stream().toList();
        onConflict = onConflict == null ? SeatConflictPolicy.FAIL : onConflict;
    }

    /**
     * Una butaca dentro del lote.
     */
    public record SeatDefinition(
            @NotBlank @SeatRow String row,
            @NotBlank @Size(max = MAX_COLUMN_LENGTH) String column,
            @NotNull SeatType seatType
    ) {
    }
}
