package tp.appsinter.cine.screening;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tp.appsinter.cine.reservation.ReservationService;
import tp.appsinter.cine.screening.dto.request.ScreeningRequestDTO;
import tp.appsinter.cine.screening.dto.response.ScreeningResponseDTO;
import tp.appsinter.cine.screening.dto.response.SeatMapResponseDTO;
import tp.appsinter.cine.screening.dto.response.SeatStateResponseDTO;
import tp.appsinter.cine.shared.dto.request.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenings")
@RequiredArgsConstructor
@Tag(name = "Movie screenings")
public class MovieScreeningController {

    private static final String SEAT_MAP = "seatMap";

    private final MovieScreeningService movieScreeningService;
    private final ReservationService reservationService;
    private final SeatStreamService seatStreamService;

    @PostMapping
    public ResponseEntity<ScreeningResponseDTO> create(
            @Valid @RequestBody ScreeningRequestDTO request) {
        ScreeningResponseDTO created = movieScreeningService.create(request);
        return ResponseEntity.created(URI.create("/api/screenings/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<ScreeningResponseDTO> findAll(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean upcomingOnly,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "true") boolean includeAvailability,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @AllowedSort({"startsAt", "endsAt", "basePrice"}) Pageable pageable) {
        return movieScreeningService.search(movieId, roomId, from, to, upcomingOnly, includeAvailability,
                includeArchived, viewer(authentication), pageable);
    }

    /**
     * Una función. Con {@code include=seatMap} trae además su mapa de butacas, con la misma
     * semántica que {@code GET /{uuid}/seats}: el selector de butacas arranca con una sola petición.
     *
     * @param include partes adicionales a incluir; hoy sólo {@code seatMap}
     */
    @GetMapping("/{uuid}")
    public ScreeningResponseDTO findById(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(defaultValue = "true") boolean includeAvailability,
            @Parameter(description = "Extra parts to embed",
                    array = @ArraySchema(schema = @Schema(allowableValues = SEAT_MAP)))
            @RequestParam(required = false) @Nullable List<String> include) {
        boolean withSeatMap = includes(include);
        String viewer = viewer(authentication);
        ScreeningResponseDTO screening = movieScreeningService.findById(uuid, includeAvailability, viewer);
        return withSeatMap ? screening.withSeatMap(reservationService.findSeatMap(uuid, viewer)) : screening;
    }

    /**
     * Valida {@code include} antes de tocar la base: un valor desconocido es un error del cliente,
     * no algo para ignorar en silencio.
     */
    private static boolean includes(@Nullable List<String> include) {
        if (include == null) {
            return false;
        }
        for (String part : include) {
            if (!SEAT_MAP.equals(part)) {
                throw BusinessRuleException.invalidField("include", "OneOf",
                        "must be one of: " + SEAT_MAP, Map.of("values", List.of(SEAT_MAP)));
            }
        }
        return !include.isEmpty();
    }

    /**
     * Archiva o restaura la función con {@code {"archived": true|false}}. Una función que todavía no
     * empezó sólo se archiva si no tiene entradas vigentes; restaurarla vuelve a ocupar su horario.
     */
    @PatchMapping("/{uuid}")
    public ScreeningResponseDTO archive(@PathVariable UUID uuid,
                                                   @Valid @RequestBody ArchiveRequestDTO request) {
        return movieScreeningService.archive(uuid, request.archived());
    }

    /**
     * Mapa de butacas. Es público, pero si quien consulta está autenticado se le muestran como
     * disponibles las butacas que él mismo tiene bloqueadas.
     */
    @GetMapping("/{uuid}/seats")
    public SeatMapResponseDTO findSeatMap(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.findSeatMap(uuid, viewer(authentication));
    }

    /**
     * Canal Server-Sent Events con los cambios de disponibilidad del mapa de butacas. Envía un evento
     * {@code seats} con {@code [{seatId, available, held}]} por cada bloqueo, liberación, vencimiento,
     * venta o cancelación, y un comentario de keep-alive periódico. Como el mapa, es público y, con
     * token, los bloqueos propios se ven disponibles.
     */
    @Operation(summary = "Seat availability stream (Server-Sent Events)", responses = @ApiResponse(
            responseCode = "200",
            description = "text/event-stream. Each `seats` event carries a JSON array of SeatState for the seats "
                    + "that changed; comment lines are keep-alives. Reconnect when the stream ends.",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = SeatStateResponseDTO.class)))))
    @GetMapping(path = "/{uuid}/seats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeatMap(Authentication authentication, @PathVariable UUID uuid) {
        return seatStreamService.subscribe(uuid, viewer(authentication));
    }

    @PutMapping("/{uuid}")
    public ScreeningResponseDTO update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody ScreeningRequestDTO request) {
        return movieScreeningService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        movieScreeningService.delete(uuid);
    }

    /**
     * Correo de quien consulta, o {@code null} si es anónimo. Estos endpoints son públicos: la
     * identidad sólo sirve para no contar los bloqueos propios como butacas ocupadas.
     */
    private static @Nullable String viewer(@Nullable Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
