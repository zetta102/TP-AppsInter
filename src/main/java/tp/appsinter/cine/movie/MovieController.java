package tp.appsinter.cine.movie;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.movie.dto.request.MovieRequestDTO;
import tp.appsinter.cine.movie.dto.response.MovieResponseDTO;
import tp.appsinter.cine.shared.dto.request.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@Tag(name = "Movies")
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<MovieResponseDTO> create(@Valid @RequestBody MovieRequestDTO request) {
        MovieResponseDTO created = movieService.create(request);
        return ResponseEntity.created(URI.create("/api/movies/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<MovieResponseDTO> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<MovieGenre> genre,
            @RequestParam(required = false) Integer releaseYear,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @AllowedSort({"movieName", "runtime", "releaseYear", "director", "originalLanguage",
                    "certification"}) Pageable pageable) {
        return movieService.findAll(search, genre, releaseYear, includeArchived, pageable);
    }

    @GetMapping("/{uuid}")
    public MovieResponseDTO findById(@PathVariable UUID uuid) {
        return movieService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public MovieResponseDTO update(@PathVariable UUID uuid, @Valid @RequestBody MovieRequestDTO request) {
        return movieService.update(uuid, request);
    }

    /**
     * Archiva o restaura la película con {@code {"archived": true|false}}. Archivarla exige que no
     * le queden funciones vigentes sin terminar.
     */
    @PatchMapping("/{uuid}")
    public MovieResponseDTO archive(@PathVariable UUID uuid, @Valid @RequestBody ArchiveRequestDTO request) {
        return movieService.archive(uuid, request.archived());
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        movieService.delete(uuid);
    }
}
