package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateMovieDTO;
import tp.appsinter.cine.model.dto.response.CreateMovieResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.utils.MovieGenre;
import tp.appsinter.cine.repository.MovieRepository;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    private static String normalise(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    @Transactional
    public CreateMovieResponseDTO create(CreateMovieDTO request) {
        Movie movie = Movie.builder()
                .movieName(request.movieName())
                .genres(new ArrayList<>(request.genres()))
                .runtime(request.runtime())
                .description(request.description())
                .director(request.director())
                .mainCast(new ArrayList<>(request.mainCast()))
                .build();
        return CreateMovieResponseDTO.from(movieRepository.save(movie));
    }

    public PageResponseDTO<CreateMovieResponseDTO> findAll(String search, MovieGenre genre, Pageable pageable) {
        return PageResponseDTO.from(
                movieRepository.search(normalise(search), genre, pageable),
                CreateMovieResponseDTO::from);
    }

    public CreateMovieResponseDTO findById(UUID uuid) {
        return CreateMovieResponseDTO.from(getMovie(uuid));
    }

    @Transactional
    public CreateMovieResponseDTO update(UUID uuid, CreateMovieDTO request) {
        Movie movie = getMovie(uuid);
        movie.setMovieName(request.movieName());
        movie.setGenres(new ArrayList<>(request.genres()));
        movie.setRuntime(request.runtime());
        movie.setDescription(request.description());
        movie.setDirector(request.director());
        movie.setMainCast(new ArrayList<>(request.mainCast()));
        return CreateMovieResponseDTO.from(movie);
    }

    @Transactional
    public void delete(UUID uuid) {
        movieRepository.delete(getMovie(uuid));
    }

    Movie getMovie(UUID uuid) {
        return movieRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie", uuid));
    }
}
