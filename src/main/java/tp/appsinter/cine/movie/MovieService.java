package tp.appsinter.cine.movie;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.movie.dto.request.MovieRequestDTO;
import tp.appsinter.cine.movie.dto.response.MovieResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.persistence.CriteriaSupport;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;
    private final MovieScreenings movieScreenings;
    private final Clock clock;

    @Transactional
    public MovieResponseDTO create(MovieRequestDTO request) {
        Movie movie = Movie.builder()
                .movieName(request.movieName())
                .genres(new ArrayList<>(request.genres()))
                .runtime(request.runtime())
                .description(request.description())
                .director(request.director())
                .mainCast(new ArrayList<>(request.mainCast()))
                .build();
        apply(request, movie);
        return MovieResponseDTO.from(movieRepository.save(movie));
    }

    /**
     * @param genres géneros a coincidir; una película entra si tiene al menos uno de ellos
     */
    public PageResponseDTO<MovieResponseDTO> findAll(@Nullable String search,
                                                           @Nullable List<MovieGenre> genres,
                                                           @Nullable Integer releaseYear,
                                                           boolean includeArchived,
                                                           Pageable pageable) {
        List<MovieGenre> requested = genres == null || genres.isEmpty() ? null : genres;
        return PageResponseDTO.from(
                movieRepository.search(CriteriaSupport.containsPattern(search), requested, releaseYear,
                        includeArchived, pageable),
                MovieResponseDTO::from);
    }

    public MovieResponseDTO findById(UUID uuid) {
        return MovieResponseDTO.from(getMovie(uuid));
    }

    /**
     * Edita una película.
     *
     * <p>La duración no puede cambiar mientras la película tenga funciones sin terminar
     * ({@code 409 RESOURCE_IN_USE}): el fin de cada función se calculó con la duración vigente al
     * programarla, y cambiarla por debajo dejaría horarios que ya no alcanzan o que pisan a la
     * función siguiente. Se reprograman o se archivan primero.
     */
    @Transactional
    public MovieResponseDTO update(UUID uuid, MovieRequestDTO request) {
        Movie movie = getMovie(uuid);
        if (!movie.getRuntime().equals(request.runtime())
                && movieScreenings.hasUpcoming(uuid, OffsetDateTime.now(clock))) {
            throw ConflictException.resourceInUse(
                    "Movie %s has upcoming screenings; its runtime cannot change".formatted(uuid),
                    "Movie", "Movie screening");
        }
        movie.setMovieName(request.movieName());
        movie.setGenres(new ArrayList<>(request.genres()));
        movie.setRuntime(request.runtime());
        movie.setDescription(request.description());
        movie.setDirector(request.director());
        movie.setMainCast(new ArrayList<>(request.mainCast()));
        apply(request, movie);
        return MovieResponseDTO.from(movie);
    }

    /**
     * Copia los campos de presentación. Se aplican tal cual, incluidos los nulos, para que editar
     * una película pueda además vaciar un póster que ya no sirve.
     */
    private void apply(MovieRequestDTO request, Movie movie) {
        movie.setPosterUrl(request.posterUrl());
        movie.setBackdropUrl(request.backdropUrl());
        movie.setReleaseYear(request.releaseYear());
        movie.setCertification(request.certification());
        movie.setOriginalLanguage(request.originalLanguage());
        movie.setTrailerUrl(request.trailerUrl());
    }

    /**
     * Archiva o restaura una película. Archivarla exige que no le queden funciones vigentes sin
     * terminar ({@code 409 RESOURCE_IN_USE}): la cartelera seguiría ofreciendo algo que ya no figura.
     */
    @Transactional
    public MovieResponseDTO archive(UUID uuid, boolean archived) {
        Movie movie = getMovie(uuid);
        if (archived == movie.isArchived()) {
            return MovieResponseDTO.from(movie);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (archived && movieScreenings.hasUpcoming(uuid, now)) {
            throw ConflictException.resourceInUse(
                    "Movie %s still has upcoming screenings; archive them first".formatted(uuid),
                    "Movie", "Movie screening");
        }
        movie.setArchivedAt(archived ? now : null);
        return MovieResponseDTO.from(movie);
    }

    @Transactional
    public void delete(UUID uuid) {
        movieRepository.delete(getMovie(uuid));
    }

    public Movie getMovie(UUID uuid) {
        return movieRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie", uuid));
    }

    /**
     * Como {@link #getMovie}, pero rechaza una película archivada ({@code 422 RESOURCE_ARCHIVED}).
     */
    public Movie getActiveMovie(UUID uuid) {
        Movie movie = getMovie(uuid);
        if (movie.isArchived()) {
            throw BusinessRuleException.resourceArchived("Movie", uuid);
        }
        return movie;
    }
}
