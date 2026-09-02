package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateMovieScreeningDTO;
import tp.appsinter.cine.model.dto.response.CreateMovieScreeningResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatMapResponseDTO;
import tp.appsinter.cine.service.MovieScreeningService;
import tp.appsinter.cine.service.ReservationService;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenings")
@RequiredArgsConstructor
@Tag(name = "Movie screenings")
public class MovieScreeningController {

    private final MovieScreeningService movieScreeningService;
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CreateMovieScreeningResponseDTO> create(
            @Valid @RequestBody CreateMovieScreeningDTO request) {
        CreateMovieScreeningResponseDTO created = movieScreeningService.create(request);
        return ResponseEntity.created(URI.create("/api/screenings/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<CreateMovieScreeningResponseDTO> findAll(
            @RequestParam(defaultValue = "false") boolean upcomingOnly,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        return movieScreeningService.search(movieId, roomId, from, to, upcomingOnly, pageable);
    }

    @GetMapping("/{uuid}")
    public CreateMovieScreeningResponseDTO findById(@PathVariable UUID uuid) {
        return movieScreeningService.findById(uuid);
    }

    @GetMapping("/{uuid}/seats")
    public SeatMapResponseDTO findSeatMap(@PathVariable UUID uuid) {
        return reservationService.findSeatMap(uuid);
    }

    @PutMapping("/{uuid}")
    public CreateMovieScreeningResponseDTO update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody CreateMovieScreeningDTO request) {
        return movieScreeningService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        movieScreeningService.delete(uuid);
    }
}
