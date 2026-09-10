package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateMovieDTO;
import tp.appsinter.cine.model.dto.response.CreateMovieResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.utils.MovieGenre;
import tp.appsinter.cine.service.MovieService;

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
    public ResponseEntity<CreateMovieResponseDTO> create(@Valid @RequestBody CreateMovieDTO request) {
        CreateMovieResponseDTO created = movieService.create(request);
        return ResponseEntity.created(URI.create("/api/movies/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<CreateMovieResponseDTO> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<MovieGenre> genre,
            @RequestParam(required = false) Integer releaseYear,
            @PageableDefault(size = 20, sort = "movieName") Pageable pageable) {
        return movieService.findAll(search, genre, releaseYear, pageable);
    }

    @GetMapping("/{uuid}")
    public CreateMovieResponseDTO findById(@PathVariable UUID uuid) {
        return movieService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public CreateMovieResponseDTO update(@PathVariable UUID uuid, @Valid @RequestBody CreateMovieDTO request) {
        return movieService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        movieService.delete(uuid);
    }
}
