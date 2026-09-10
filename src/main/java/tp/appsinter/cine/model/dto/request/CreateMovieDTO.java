package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.*;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.time.Year;
import java.util.List;

/**
 * Alta y edición de una película del catálogo.
 *
 * <p>Los campos de presentación son opcionales pero validados: una dirección mal formada llegaría
 * hasta la etiqueta {@code img} del navegador y se vería como una tarjeta rota, no como un error.
 */
public record CreateMovieDTO(
        @NotBlank String movieName,
        @NotEmpty List<MovieGenre> genres,
        @NotNull @Positive Integer runtime,
        @Size(max = 2000) String description,
        String director,
        List<String> mainCast,
        @Pattern(regexp = ABSOLUTE_HTTP_URL, message = "must be an absolute http(s) URL")
        @Size(max = 2048) String posterUrl,
        @Pattern(regexp = ABSOLUTE_HTTP_URL, message = "must be an absolute http(s) URL")
        @Size(max = 2048) String backdropUrl,
        @Min(EARLIEST_RELEASE_YEAR) Integer releaseYear,
        @Size(max = 16) String certification,
        @Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") String originalLanguage,
        @Pattern(regexp = ABSOLUTE_HTTP_URL, message = "must be an absolute http(s) URL")
        @Size(max = 2048) String trailerUrl
) {

    /**
     * 1888 es el año de <i>Roundhay Garden Scene</i>, la película más antigua que se conserva.
     */
    public static final int EARLIEST_RELEASE_YEAR = 1888;

    /**
     * Margen hacia el futuro para poder anunciar estrenos ya confirmados.
     */
    public static final int RELEASE_YEAR_LOOKAHEAD = 10;

    static final String ABSOLUTE_HTTP_URL = "^https?://\\S+$";

    public CreateMovieDTO {
        genres = genres == null ? List.of() : List.copyOf(genres);
        mainCast = mainCast == null ? List.of() : List.copyOf(mainCast);
    }

    /**
     * El límite superior depende del año en curso, así que no puede expresarse con {@code @Max}.
     */
    @AssertTrue(message = "must not be more than " + RELEASE_YEAR_LOOKAHEAD + " years in the future")
    public boolean isReleaseYearInRange() {
        return releaseYear == null || releaseYear <= Year.now().getValue() + RELEASE_YEAR_LOOKAHEAD;
    }
}
