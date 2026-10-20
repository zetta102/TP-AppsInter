package tp.appsinter.cine.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.shared.validation.SeatRow;

import java.util.List;

/**
 * Generación de una grilla rectangular de butacas.
 *
 * <p>Una sala típica es un rectángulo con algunas filas premium al fondo y algún hueco de pasillo.
 * Describirla así convierte doscientas peticiones en una, y evita que el cliente tenga que fabricar
 * nombres de fila.
 *
 * @param rows        cantidad de filas, nombradas correlativamente desde {@code startRow}
 * @param columns     cantidad de butacas por fila, numeradas desde 1
 * @param startRow    nombre de la primera fila (una o dos letras); por omisión {@code A}, o la
 *                    siguiente libre si la sala ya tiene filas
 * @param premiumRows filas que se marcan como premium; el resto quedan normales
 * @param skip        posiciones a saltear, escritas como {@code E5}, para dejar pasillos
 * @param onConflict  qué hacer si alguna posición generada ya existe
 */
@Schema(name = "GenerateSeatsRequest")
public record GenerateSeatsRequestDTO(
        @NotNull @Positive @Max(MAX_ROWS) Integer rows,
        @NotNull @Positive @Max(MAX_COLUMNS) Integer columns,
        @SeatRow @Nullable String startRow,
        @Nullable List<@NotNull @SeatRow String> premiumRows,
        @Nullable List<@NotNull @Pattern(regexp = "^[A-Za-z]{1,2} ?[0-9]{1,3}$",
                message = "must be a seat position such as E5") String> skip,
        @Nullable SeatConflictPolicy onConflict
) {

    /**
     * Techos deliberadamente generosos. No describen ninguna sala real; están para que un cero de
     * más en la petición no intente crear un millón de filas.
     */
    public static final int MAX_ROWS = 100;
    public static final int MAX_COLUMNS = 100;

    public GenerateSeatsRequestDTO {
        premiumRows = premiumRows == null ? List.of() : premiumRows.stream().toList();
        skip = skip == null ? List.of() : skip.stream().toList();
        onConflict = onConflict == null ? SeatConflictPolicy.FAIL : onConflict;
    }
}
