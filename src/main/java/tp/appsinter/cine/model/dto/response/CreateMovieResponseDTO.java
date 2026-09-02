package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.util.List;
import java.util.UUID;

public record CreateMovieResponseDTO(
        UUID uuid,
        String movieName,
        List<MovieGenre> genres,
        Integer runtime,
        String description,
        String director,
        List<String> mainCast
) {
    public static CreateMovieResponseDTO from(Movie movie) {
        return new CreateMovieResponseDTO(
                movie.getUuid(),
                movie.getMovieName(),
                List.copyOf(movie.getGenres()),
                movie.getRuntime(),
                movie.getDescription(),
                movie.getDirector(),
                List.copyOf(movie.getMainCast())
        );
    }
}
