package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateMovieScreeningDTO;
import tp.appsinter.cine.model.dto.response.CreateMovieScreeningResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.entities.MovieScreening;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.utils.BusinessConstants;
import tp.appsinter.cine.repository.MovieScreeningRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieScreeningService {

    /**
     * Hueco reservado entre funciones para limpieza y recambio de público. Se publica en
     * {@code GET /api/metadata/config} desde la misma constante, para que el cliente no lo repita.
     */
    private static final int TURNOVER_MINUTES = BusinessConstants.SCREENING_TURNOVER_MINUTES;

    private final MovieScreeningRepository movieScreeningRepository;
    private final MovieService movieService;
    private final ScreenRoomService screenRoomService;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public CreateMovieScreeningResponseDTO create(CreateMovieScreeningDTO request) {
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);

        requireNoOverlap(room, startsAt, endsAt, null);

        MovieScreening screening = MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .basePrice(request.basePrice())
                .build();
        return describe(List.of(movieScreeningRepository.save(screening)), true).getFirst();
    }

    public PageResponseDTO<CreateMovieScreeningResponseDTO> search(UUID movieId,
                                                                   UUID roomId,
                                                                   OffsetDateTime from,
                                                                   OffsetDateTime to,
                                                                   boolean upcomingOnly,
                                                                   boolean includeAvailability,
                                                                   Pageable pageable) {
        if (movieId != null) {
            movieService.getMovie(movieId);
        }
        if (roomId != null) {
            screenRoomService.getScreenRoom(roomId);
        }
        // upcomingOnly es un atajo de "desde ahora" y nunca pisa un from explícito.
        OffsetDateTime effectiveFrom = from != null ? from : (upcomingOnly ? OffsetDateTime.now() : null);
        Page<MovieScreening> page = movieScreeningRepository.search(movieId, roomId, effectiveFrom, to, pageable);
        List<CreateMovieScreeningResponseDTO> content = describe(page.getContent(), includeAvailability);
        return PageResponseDTO.from(page, content);
    }

    public List<CreateMovieScreeningResponseDTO> findAll(boolean includeAvailability) {
        return describe(movieScreeningRepository.findAll(), includeAvailability);
    }

    public List<CreateMovieScreeningResponseDTO> findUpcoming(boolean includeAvailability) {
        return describe(
                movieScreeningRepository.findByStartsAtAfterOrderByStartsAtAsc(OffsetDateTime.now()),
                includeAvailability);
    }

    public List<CreateMovieScreeningResponseDTO> findByMovie(UUID movieId, boolean includeAvailability) {
        movieService.getMovie(movieId);
        return describe(movieScreeningRepository.findByMovieUuid(movieId), includeAvailability);
    }

    public CreateMovieScreeningResponseDTO findById(UUID uuid, boolean includeAvailability) {
        return describe(List.of(getScreening(uuid)), includeAvailability).getFirst();
    }

    /**
     * Convierte funciones a su representación pública, resolviendo la disponibilidad de todo el lote
     * con dos consultas agrupadas en lugar de dos por función.
     *
     * <p>Cuando {@code includeAvailability} es falso los tres campos quedan nulos y no se toca la
     * base: es la salida para quien sólo quiere la grilla de horarios.
     */
    private List<CreateMovieScreeningResponseDTO> describe(List<MovieScreening> screenings,
                                                           boolean includeAvailability) {
        if (!includeAvailability || screenings.isEmpty()) {
            return screenings.stream().map(CreateMovieScreeningResponseDTO::from).toList();
        }
        Set<UUID> roomIds = screenings.stream()
                .map(screening -> screening.getScreenRoom().getUuid())
                .collect(Collectors.toSet());
        Set<UUID> screeningIds = screenings.stream().map(MovieScreening::getUuid).collect(Collectors.toSet());

        Map<UUID, Integer> capacity = tally(seatRepository.countByScreenRoom(roomIds));
        Map<UUID, Integer> booked = tally(ticketRepository.countBookedByScreening(screeningIds));

        return screenings.stream()
                .map(screening -> CreateMovieScreeningResponseDTO.from(
                        screening,
                        capacity.getOrDefault(screening.getScreenRoom().getUuid(), 0),
                        booked.getOrDefault(screening.getUuid(), 0)))
                .toList();
    }

    /**
     * Aplana las tuplas {@code (uuid, count)} de una consulta agrupada en un mapa consultable.
     */
    private Map<UUID, Integer> tally(List<Object[]> rows) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    @Transactional
    public CreateMovieScreeningResponseDTO update(UUID uuid, CreateMovieScreeningDTO request) {
        MovieScreening screening = getScreening(uuid);
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);

        requireNoOverlap(room, startsAt, endsAt, uuid);

        screening.setMovie(movie);
        screening.setScreenRoom(room);
        screening.setStartsAt(startsAt);
        screening.setEndsAt(endsAt);
        screening.setBasePrice(request.basePrice());
        return describe(List.of(screening), true).getFirst();
    }

    @Transactional
    public void delete(UUID uuid) {
        movieScreeningRepository.delete(getScreening(uuid));
    }

    /**
     * Rechaza una función cuya ventana {@code [startsAt, endsAt)} pise a otra de la misma sala. El
     * identificador excluido permite que una actualización no choque consigo misma.
     */
    private void requireNoOverlap(ScreenRoom room,
                                  OffsetDateTime startsAt,
                                  OffsetDateTime endsAt,
                                  UUID excludedUuid) {
        movieScreeningRepository.findFirstOverlapInRoom(room.getUuid(), startsAt, endsAt, excludedUuid)
                .ifPresent(conflict -> {
                    throw ConflictException.screeningOverlap(
                            "Room %s already has a screening overlapping %s - %s"
                                    .formatted(room.getRoomName(), startsAt, endsAt),
                            conflict.getUuid(), conflict.getStartsAt(), conflict.getEndsAt());
                });
    }

    MovieScreening getScreening(UUID uuid) {
        return movieScreeningRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }
}
