package tp.appsinter.cine.screening;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.hold.SeatHoldRepository;
import tp.appsinter.cine.movie.Movie;
import tp.appsinter.cine.movie.MovieRepository;
import tp.appsinter.cine.movie.MovieService;
import tp.appsinter.cine.movie.dto.MovieResponseDTO;
import tp.appsinter.cine.movie.dto.NowShowingResponseDTO;
import tp.appsinter.cine.reservation.TicketRepository;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomService;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.screening.dto.CreateMovieScreeningRequestDTO;
import tp.appsinter.cine.screening.dto.MovieScreeningResponseDTO;
import tp.appsinter.cine.shared.BusinessConstants;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.user.UserService;

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
    private final MovieRepository movieRepository;
    private final MovieService movieService;
    private final ScreenRoomService screenRoomService;
    private final UserService userService;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final SeatHoldRepository seatHoldRepository;

    @Transactional
    public MovieScreeningResponseDTO create(CreateMovieScreeningRequestDTO request) {
        Movie movie = movieService.getActiveMovie(request.movieId());
        ScreenRoom room = screenRoomService.getActiveScreenRoom(request.screenRoomId());

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
     * @param viewerEmail     quien consulta, o {@code null} si es anónimo; sus propios bloqueos de
     *                        butacas no se descuentan de la disponibilidad, igual que en el mapa
     * @param includeArchived si es falso, las funciones archivadas no aparecen
     */
    public PageResponseDTO<MovieScreeningResponseDTO> search(@Nullable UUID movieId,
                                                                   @Nullable UUID roomId,
                                                                   @Nullable OffsetDateTime from,
                                                                   @Nullable OffsetDateTime to,
                                                                   boolean upcomingOnly,
                                                                   boolean includeAvailability,
                                                                   boolean includeArchived,
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
        Page<MovieScreening> page = movieScreeningRepository.search(
                movieId, roomId, effectiveFrom, to, includeArchived, pageable);
        List<MovieScreeningResponseDTO> content =
                describe(page.getContent(), includeAvailability, viewer(viewerEmail));
        return PageResponseDTO.from(page, content);
    }

    public MovieScreeningResponseDTO findById(UUID uuid,
                                                    boolean includeAvailability,
                                                    @Nullable String viewerEmail) {
        return describe(List.of(getScreening(uuid)), includeAvailability, viewer(viewerEmail)).getFirst();
    }

    /**
     * Cartelera agregada: una página de películas vigentes con funciones vigentes que empiezan en la
     * ventana pedida, cada una con esas funciones en orden cronológico y con su disponibilidad.
     *
     * <p>Son tres pasos de consultas fijas por página —películas, funciones de esas películas y la
     * disponibilidad de todas ellas en lote—, sin importar cuántas películas o funciones entren.
     *
     * @param from desde cuándo; {@code null} es «ahora»
     * @param to   hasta cuándo; {@code null} no pone límite
     */
    public PageResponseDTO<NowShowingResponseDTO> nowShowing(@Nullable OffsetDateTime from,
                                                             @Nullable OffsetDateTime to,
                                                             @Nullable String viewerEmail,
                                                             Pageable pageable) {
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now();
        Page<Movie> movies = movieRepository.findNowShowing(effectiveFrom, to, pageable);
        if (movies.isEmpty()) {
            return PageResponseDTO.from(movies, List.of());
        }
        List<UUID> movieIds = movies.getContent().stream().map(Movie::getUuid).toList();
        List<MovieScreening> screenings = movieScreeningRepository.findShowingForMovies(movieIds, effectiveFrom, to);
        Map<UUID, List<MovieScreeningResponseDTO>> byMovie = describe(screenings, true, viewer(viewerEmail))
                .stream()
                .collect(Collectors.groupingBy(MovieScreeningResponseDTO::movieId));
        return PageResponseDTO.from(movies, movie -> new NowShowingResponseDTO(
                MovieResponseDTO.from(movie),
                byMovie.getOrDefault(movie.getUuid(), List.of())));
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
    private List<MovieScreeningResponseDTO> describe(List<MovieScreening> screenings,
                                                           boolean includeAvailability,
                                                           @Nullable UUID viewerUuid) {
        if (!includeAvailability || screenings.isEmpty()) {
            return screenings.stream().map(MovieScreeningResponseDTO::from).toList();
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
                .map(screening -> MovieScreeningResponseDTO.from(
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
    public MovieScreeningResponseDTO update(UUID uuid, CreateMovieScreeningRequestDTO request) {
        MovieScreening screening = getScreening(uuid);
        boolean movieChanged = !screening.getMovie().getUuid().equals(request.movieId());
        boolean roomChanged = !screening.getScreenRoom().getUuid().equals(request.screenRoomId());
        // Una película o sala archivada no recibe funciones nuevas, pero las que ya tenía siguen
        // admitiendo cambios de precio.
        Movie movie = movieChanged
                ? movieService.getActiveMovie(request.movieId())
                : screening.getMovie();
        ScreenRoom room = roomChanged
                ? screenRoomService.getActiveScreenRoom(request.screenRoomId())
                : screening.getScreenRoom();

        OffsetDateTime startsAt = request.startsAt();
        boolean timeChanged = !sameInstant(screening.getStartsAt(), startsAt);
        boolean rescheduled = timeChanged || movieChanged || roomChanged;

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

    /**
     * Archiva o restaura una función.
     *
     * <p>Archivar una función que todavía no empezó exige que no tenga entradas vigentes
     * ({@code 409 RESOURCE_IN_USE}): quien las compró espera esa función. Una ya empezada, o una con
     * entradas sólo canceladas, se archiva siempre. Restaurarla vuelve a ocupar su horario, así que
     * exige que nadie lo haya tomado ({@code 409 SCREENING_OVERLAP}) y que la película y la sala
     * sigan vigentes ({@code 422 RESOURCE_ARCHIVED}).
     */
    @Transactional
    public MovieScreeningResponseDTO archive(UUID uuid, boolean archived) {
        MovieScreening screening = lockScreening(uuid);
        if (archived != screening.isArchived()) {
            OffsetDateTime now = OffsetDateTime.now();
            if (archived) {
                if (screening.getStartsAt().isAfter(now)) {
                    requireUnsold(screening);
                }
                screening.setArchivedAt(now);
            } else {
                movieService.getActiveMovie(screening.getMovie().getUuid());
                screenRoomService.getActiveScreenRoom(screening.getScreenRoom().getUuid());
                requireNoOverlap(screening.getScreenRoom(), screening.getStartsAt(), screening.getEndsAt(), uuid);
                screening.setArchivedAt(null);
            }
        }
        return describe(List.of(screening), true, null).getFirst();
    }

    @Transactional
    public void delete(UUID uuid) {
        movieScreeningRepository.delete(getScreening(uuid));
    }

    /**
     * Rechaza operar sobre una función archivada ({@code 422 RESOURCE_ARCHIVED}): no admite
     * bloqueos, presupuestos ni compras nuevas.
     */
    public static void requireActive(MovieScreening screening) {
        if (screening.isArchived()) {
            throw BusinessRuleException.resourceArchived("Movie screening", screening.getUuid());
        }
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
            throw BusinessRuleException.invalidField("startsAt", "Future", "must be a future date");
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

    public MovieScreening getScreening(UUID uuid) {
        return movieScreeningRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }

    /**
     * Como {@link #getScreening}, pero bloquea la fila de la función hasta que termine la
     * transacción. Lo usan las operaciones que comprueban y luego ocupan butacas.
     */
    public MovieScreening lockScreening(UUID uuid) {
        return movieScreeningRepository.findByIdForUpdate(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }
}
