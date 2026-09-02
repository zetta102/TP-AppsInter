package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.*;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.util.List;

public record CreateMovieDTO(
        @NotBlank String movieName,
        @NotEmpty List<MovieGenre> genres,
        @NotNull @Positive Integer runtime,
        @Size(max = 2000) String description,
        String director,
        List<String> mainCast
) {
    public CreateMovieDTO {
        genres = genres == null ? List.of() : List.copyOf(genres);
        mainCast = mainCast == null ? List.of() : List.copyOf(mainCast);
    }
}
