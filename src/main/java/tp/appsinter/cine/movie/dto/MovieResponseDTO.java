package tp.appsinter.cine.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.movie.Movie;
import tp.appsinter.cine.movie.MovieGenre;

import java.util.List;
import java.util.UUID;

@Schema(name = "Movie")
public record MovieResponseDTO(
        UUID uuid,
        String movieName,
        List<MovieGenre> genres,
        Integer runtime,
        @Nullable String description,
        @Nullable String director,
        List<String> mainCast,
        @Nullable String posterUrl,
        @Nullable String backdropUrl,
        @Nullable Integer releaseYear,
        @Nullable String certification,
        @Nullable String originalLanguage,
        @Nullable String trailerUrl,
        boolean archived
) {
    public static MovieResponseDTO from(Movie movie) {
        return new MovieResponseDTO(
                movie.getUuid(),
                movie.getMovieName(),
                List.copyOf(movie.getGenres()),
                movie.getRuntime(),
                movie.getDescription(),
                movie.getDirector(),
                List.copyOf(movie.getMainCast()),
                movie.getPosterUrl(),
                movie.getBackdropUrl(),
                movie.getReleaseYear(),
                movie.getCertification(),
                movie.getOriginalLanguage(),
                movie.getTrailerUrl(),
                movie.isArchived()
        );
    }
}
