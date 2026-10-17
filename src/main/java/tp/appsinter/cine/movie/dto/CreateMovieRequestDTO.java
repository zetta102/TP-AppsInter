package tp.appsinter.cine.movie.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.shared.validation.HttpUrl;

import java.time.Year;
import java.util.List;

/**
 * Alta y edición de una película del catálogo.
 *
 * <p>Los campos de presentación son opcionales pero validados: una dirección mal formada llegaría
 * hasta la etiqueta {@code img} del navegador y se vería como una tarjeta rota, no como un error.
 */
@Schema(name = "MovieRequest")
public record CreateMovieRequestDTO(
        @NotBlank String movieName,
        @NotEmpty List<MovieGenre> genres,
        @NotNull @Positive Integer runtime,
        @Size(max = 2000) @Nullable String description,
        @Nullable String director,
        @Nullable List<String> mainCast,
        @HttpUrl
        @Size(max = 2048) @Nullable String posterUrl,
        @HttpUrl
        @Size(max = 2048) @Nullable String backdropUrl,
        @Min(EARLIEST_RELEASE_YEAR) @Nullable Integer releaseYear,
        @Size(max = 16) @Nullable String certification,
        @Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") @Nullable String originalLanguage,
        @HttpUrl
        @Size(max = 2048) @Nullable String trailerUrl
) {

    /**
     * 1888 es el año de <i>Roundhay Garden Scene</i>, la película más antigua que se conserva.
     */
    public static final int EARLIEST_RELEASE_YEAR = 1888;

    /**
     * Margen hacia el futuro para poder anunciar estrenos ya confirmados.
     */
    public static final int RELEASE_YEAR_LOOKAHEAD = 10;

    public CreateMovieRequestDTO {
        genres = genres == null ? List.of() : List.copyOf(genres);
        mainCast = mainCast == null ? List.of() : List.copyOf(mainCast);
    }

    /**
     * El límite superior depende del año en curso, así que no puede expresarse con {@code @Max}.
     */
    @JsonIgnore
    @AssertTrue(message = "must not be more than " + RELEASE_YEAR_LOOKAHEAD + " years in the future")
    public boolean isReleaseYearInRange() {
        return releaseYear == null || releaseYear <= Year.now().getValue() + RELEASE_YEAR_LOOKAHEAD;
    }
}
