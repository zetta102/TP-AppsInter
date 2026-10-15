package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
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
import tp.appsinter.cine.repository.SeatHoldRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    private final UserService userService;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final SeatHoldRepository seatHoldRepository;

    @Transactional
    public CreateMovieScreeningResponseDTO create(CreateMovieScreeningDTO request) {
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        requireFuture(startsAt);
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);

        requireNoOverlap(room, startsAt, endsAt, null);

        MovieScreening screening = MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .basePrice(request.basePrice())
                .build();
        return describe(List.of(movieScreeningRepository.save(screening)), true, null).getFirst();
    }

    /**
     * @param viewerEmail quien consulta, o {@code null} si es anónimo; sus propios bloqueos de
     *                    butacas no se descuentan de la disponibilidad, igual que en el mapa
     */
    public PageResponseDTO<CreateMovieScreeningResponseDTO> search(@Nullable UUID movieId,
                                                                   @Nullable UUID roomId,
                                                                   @Nullable OffsetDateTime from,
                                                                   @Nullable OffsetDateTime to,
                                                                   boolean upcomingOnly,
                                                                   boolean includeAvailability,
                                                                   @Nullable String viewerEmail,
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
        List<CreateMovieScreeningResponseDTO> content =
                describe(page.getContent(), includeAvailability, viewer(viewerEmail));
        return PageResponseDTO.from(page, content);
    }

    public CreateMovieScreeningResponseDTO findById(UUID uuid,
                                                    boolean includeAvailability,
                                                    @Nullable String viewerEmail) {
        return describe(List.of(getScreening(uuid)), includeAvailability, viewer(viewerEmail)).getFirst();
    }

    private @Nullable UUID viewer(@Nullable String viewerEmail) {
        return viewerEmail == null ? null : userService.findUuidByEmail(viewerEmail);
    }

    /**
     * Convierte funciones a su representación pública, resolviendo la disponibilidad de todo el lote
     * con tres consultas agrupadas en lugar de tres por función.
     *
     * <p>Cuando {@code includeAvailability} es falso los campos de disponibilidad quedan nulos y no
     * se toca la base: es la salida para quien sólo quiere la grilla de horarios.
     *
     * @param viewerUuid quien consulta; sus bloqueos no cuentan como ocupación, porque para esa
     *                   persona esas butacas siguen disponibles
     */
    private List<CreateMovieScreeningResponseDTO> describe(List<MovieScreening> screenings,
                                                           boolean includeAvailability,
                                                           @Nullable UUID viewerUuid) {
        if (!includeAvailability || screenings.isEmpty()) {
            return screenings.stream().map(CreateMovieScreeningResponseDTO::from).toList();
        }
        Set<UUID> roomIds = screenings.stream()
                .map(screening -> screening.getScreenRoom().getUuid())
                .collect(Collectors.toSet());
        Set<UUID> screeningIds = screenings.stream().map(MovieScreening::getUuid).collect(Collectors.toSet());

        Map<UUID, Integer> capacity = tally(seatRepository.countByScreenRoom(roomIds));
        Map<UUID, Integer> booked = tally(ticketRepository.countBookedByScreening(screeningIds));
        Map<UUID, Integer> held = tally(
                seatHoldRepository.countHeldByScreening(screeningIds, viewerUuid, OffsetDateTime.now()));

        return screenings.stream()
                .map(screening -> CreateMovieScreeningResponseDTO.from(
                        screening,
                        capacity.getOrDefault(screening.getScreenRoom().getUuid(), 0),
                        booked.getOrDefault(screening.getUuid(), 0),
                        held.getOrDefault(screening.getUuid(), 0)))
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

    /**
     * Edita una función.
     *
     * <p>Las reglas dependen de qué cambia:
     * <ul>
     *   <li>Sólo el precio base: siempre se admite, incluso con la función empezada o con entradas
     *       vendidas, porque cada entrada conserva el precio con que se vendió.</li>
     *   <li>Película, sala u horario: la función no debe haber empezado ({@code 422
     *       SCREENING_STARTED}), el horario nuevo debe ser futuro ({@code 400 VALIDATION_FAILED}),
     *       no debe pisar otra función ({@code 409 SCREENING_OVERLAP}) y no debe haber entradas
     *       vendidas ({@code 409 RESOURCE_IN_USE}).</li>
     * </ul>
     */
    @Transactional
    public CreateMovieScreeningResponseDTO update(UUID uuid, CreateMovieScreeningDTO request) {
        MovieScreening screening = getScreening(uuid);
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        boolean timeChanged = !sameInstant(screening.getStartsAt(), startsAt);
        boolean rescheduled = timeChanged
                || !screening.getMovie().getUuid().equals(movie.getUuid())
                || !screening.getScreenRoom().getUuid().equals(room.getUuid());

        if (rescheduled) {
            if (screening.getStartsAt().isBefore(OffsetDateTime.now())) {
                throw BusinessRuleException.screeningStarted(screening.getUuid(), screening.getStartsAt());
            }
            if (timeChanged) {
                requireFuture(startsAt);
            }
        }
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);
        if (rescheduled) {
            requireNoOverlap(room, startsAt, endsAt, uuid);
            requireUnsold(screening);
        }

        screening.setMovie(movie);
        screening.setScreenRoom(room);
        screening.setStartsAt(startsAt);
        screening.setEndsAt(endsAt);
        screening.setBasePrice(request.basePrice());
        return describe(List.of(screening), true, null).getFirst();
    }

    @Transactional
    public void delete(UUID uuid) {
        movieScreeningRepository.delete(getScreening(uuid));
    }

    /**
     * Postgres guarda microsegundos: se compara con esa precisión para que reenviar el horario tal
     * como se leyó no cuente como un cambio.
     */
    private static boolean sameInstant(OffsetDateTime stored, OffsetDateTime requested) {
        return stored.truncatedTo(ChronoUnit.MICROS).isEqual(requested.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Una función nueva, o una que cambia de horario, tiene que empezar en el futuro. No se expresa
     * con {@code @Future} en el DTO porque al editar sólo el precio de una función ya empezada se
     * reenvía su horario original, que ya no es futuro.
     */
    private static void requireFuture(OffsetDateTime startsAt) {
        if (!startsAt.isAfter(OffsetDateTime.now())) {
            throw BusinessRuleException.invalidField("startsAt", "must be a future date");
        }
    }

    /**
     * Rechaza una función cuya ventana {@code [startsAt, endsAt)} pise a otra de la misma sala. El
     * identificador excluido permite que una actualización no choque consigo misma.
     */
    private void requireNoOverlap(ScreenRoom room,
                                  OffsetDateTime startsAt,
                                  OffsetDateTime endsAt,
                                  @Nullable UUID excludedUuid) {
        movieScreeningRepository.findFirstOverlapInRoom(room.getUuid(), startsAt, endsAt, excludedUuid)
                .ifPresent(conflict -> {
                    throw ConflictException.screeningOverlap(
                            "Room %s already has a screening overlapping %s - %s"
                                    .formatted(room.getRoomName(), startsAt, endsAt),
                            conflict.getUuid(), conflict.getStartsAt(), conflict.getEndsAt());
                });
    }

    /**
     * Una función con entradas vendidas no puede cambiar de película, de sala ni de horario: las
     * entradas apuntan a butacas de la sala original y quien las compró eligió esa función.
     */
    private void requireUnsold(MovieScreening screening) {
        if (ticketRepository.existsByMovieScreeningUuidAndCancelledAtIsNull(screening.getUuid())) {
            throw ConflictException.resourceInUse(
                    "Screening %s already has tickets sold; only its base price can change"
                            .formatted(screening.getUuid()),
                    "Movie screening", "Ticket");
        }
    }

    MovieScreening getScreening(UUID uuid) {
        return movieScreeningRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }

    /**
     * Como {@link #getScreening}, pero bloquea la fila de la función hasta que termine la
     * transacción. Lo usan las operaciones que comprueban y luego ocupan butacas.
     */
    MovieScreening lockScreening(UUID uuid) {
        return movieScreeningRepository.findByIdForUpdate(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }
}
