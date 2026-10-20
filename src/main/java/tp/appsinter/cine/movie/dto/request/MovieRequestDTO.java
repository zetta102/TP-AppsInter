package tp.appsinter.cine.movie.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.shared.validation.HttpUrl;
import tp.appsinter.cine.shared.validation.MaxYearsAhead;

import java.util.List;

/**
 * Alta y edición de una película del catálogo.
 *
 * <p>Los campos de presentación son opcionales pero validados: una dirección mal formada llegaría
 * hasta la etiqueta {@code img} del navegador y se vería como una tarjeta rota, no como un error.
 * Los textos tienen el mismo largo máximo que su columna, para que un valor demasiado largo sea un
 * {@code 400} con el campo señalado y no un error de la base.
 *
 * @param genres      un género repetido se cuenta una sola vez
 * @param releaseYear entre 1888 —<i>Roundhay Garden Scene</i>, la película más antigua que se
 *                    conserva— y diez años por delante, para poder anunciar estrenos confirmados
 */
@Schema(name = "MovieRequest")
public record MovieRequestDTO(
        @NotBlank @Size(max = 255) String movieName,
        @NotEmpty List<@NotNull MovieGenre> genres,
        @NotNull @Positive Integer runtime,
        @Size(max = 2000) @Nullable String description,
        @Size(max = 255) @Nullable String director,
        @Nullable List<@NotBlank @Size(max = 255) String> mainCast,
        @HttpUrl
        @Size(max = 2048) @Nullable String posterUrl,
        @HttpUrl
        @Size(max = 2048) @Nullable String backdropUrl,
        @Min(EARLIEST_RELEASE_YEAR) @MaxYearsAhead(RELEASE_YEAR_LOOKAHEAD) @Nullable Integer releaseYear,
        @Size(max = 16) @Nullable String certification,
        @Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") @Nullable String originalLanguage,
        @HttpUrl
        @Size(max = 2048) @Nullable String trailerUrl
) {

    public static final int EARLIEST_RELEASE_YEAR = 1888;

    public static final int RELEASE_YEAR_LOOKAHEAD = 10;

    /**
     * Las listas se copian con {@code stream().toList()} y no con {@code List.copyOf}: ésta rechaza
     * un elemento nulo con una excepción al leer el cuerpo, en lugar de dejar que Bean Validation lo
     * informe como un error del campo.
     */
    public MovieRequestDTO {
        genres = genres == null ? List.of() : genres.stream().distinct().toList();
        mainCast = mainCast == null ? List.of() : mainCast.stream().toList();
    }
}
