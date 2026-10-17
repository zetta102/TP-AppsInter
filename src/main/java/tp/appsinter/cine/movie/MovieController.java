package tp.appsinter.cine.movie;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.movie.dto.CreateMovieRequestDTO;
import tp.appsinter.cine.movie.dto.MovieResponseDTO;
import tp.appsinter.cine.movie.dto.NowShowingResponseDTO;
import tp.appsinter.cine.screening.MovieScreeningService;
import tp.appsinter.cine.shared.dto.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@Tag(name = "Movies")
public class MovieController {

    private final MovieService movieService;
    private final MovieScreeningService movieScreeningService;

    @PostMapping
    public ResponseEntity<MovieResponseDTO> create(@Valid @RequestBody CreateMovieRequestDTO request) {
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

    /**
     * Cartelera agregada: películas vigentes con sus funciones vigentes que empiezan en
     * {@code [from, to]}, en orden cronológico y con disponibilidad. Reemplaza descargar películas y
     * funciones por separado y cruzarlas en el cliente.
     *
     * @param from desde cuándo; por omisión, ahora
     * @param to   hasta cuándo; por omisión, sin límite
     */
    @GetMapping("/now-showing")
    public PageResponseDTO<NowShowingResponseDTO> nowShowing(
            @Nullable Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @AllowedSort({"movieName", "releaseYear"}) Pageable pageable) {
        return movieScreeningService.nowShowing(from, to,
                authentication == null ? null : authentication.getName(), pageable);
    }

    @GetMapping("/{uuid}")
    public MovieResponseDTO findById(@PathVariable UUID uuid) {
        return movieService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public MovieResponseDTO update(@PathVariable UUID uuid, @Valid @RequestBody CreateMovieRequestDTO request) {
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
