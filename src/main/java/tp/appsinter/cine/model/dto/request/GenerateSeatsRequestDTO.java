package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

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
 * @param startRow    primera letra de fila; por omisión {@code A}, o la siguiente libre si la sala
 *                    ya tiene filas
 * @param premiumRows filas que se marcan como premium; el resto quedan normales
 * @param skip        posiciones a saltear, escritas como {@code E5}, para dejar pasillos
 * @param onConflict  qué hacer si alguna posición generada ya existe
 */
public record GenerateSeatsRequestDTO(
        @NotNull @Positive @Max(MAX_ROWS) Integer rows,
        @NotNull @Positive @Max(MAX_COLUMNS) Integer columns,
        @Pattern(regexp = "^[A-Z]{1,2}$", message = "must be one or two uppercase letters") String startRow,
        List<String> premiumRows,
        List<String> skip,
        SeatConflictPolicy onConflict
) {

    /**
     * Techos deliberadamente generosos. No describen ninguna sala real; están para que un cero de
     * más en la petición no intente crear un millón de filas.
     */
    public static final int MAX_ROWS = 100;
    public static final int MAX_COLUMNS = 100;

    public GenerateSeatsRequestDTO {
        premiumRows = premiumRows == null ? List.of() : List.copyOf(premiumRows);
        skip = skip == null ? List.of() : List.copyOf(skip);
        onConflict = onConflict == null ? SeatConflictPolicy.FAIL : onConflict;
    }
}
