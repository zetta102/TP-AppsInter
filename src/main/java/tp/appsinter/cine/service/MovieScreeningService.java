package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
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
import tp.appsinter.cine.repository.MovieScreeningRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieScreeningService {

    /**
     * Gap reserved between screenings for cleaning and audience turnover.
     */
    private static final int TURNOVER_MINUTES = 15;

    private final MovieScreeningRepository movieScreeningRepository;
    private final MovieService movieService;
    private final ScreenRoomService screenRoomService;

    @Transactional
    public CreateMovieScreeningResponseDTO create(CreateMovieScreeningDTO request) {
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);

        if (movieScreeningRepository.overlapsInRoom(room.getUuid(), startsAt, endsAt, null)) {
            throw new ConflictException(
                    "Room %s already has a screening overlapping %s - %s".formatted(room.getRoomName(), startsAt, endsAt));
        }

        MovieScreening screening = MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .basePrice(request.basePrice())
                .build();
        return CreateMovieScreeningResponseDTO.from(movieScreeningRepository.save(screening));
    }

    public PageResponseDTO<CreateMovieScreeningResponseDTO> search(UUID movieId,
                                                                   UUID roomId,
                                                                   OffsetDateTime from,
                                                                   OffsetDateTime to,
                                                                   boolean upcomingOnly,
                                                                   Pageable pageable) {
        if (movieId != null) {
            movieService.getMovie(movieId);
        }
        if (roomId != null) {
            screenRoomService.getScreenRoom(roomId);
        }
        // upcomingOnly is a convenience shorthand for "from now", and never overrides an explicit from.
        OffsetDateTime effectiveFrom = from != null ? from : (upcomingOnly ? OffsetDateTime.now() : null);
        return PageResponseDTO.from(
                movieScreeningRepository.search(movieId, roomId, effectiveFrom, to, pageable),
                CreateMovieScreeningResponseDTO::from);
    }

    public List<CreateMovieScreeningResponseDTO> findAll() {
        return movieScreeningRepository.findAll().stream()
                .map(CreateMovieScreeningResponseDTO::from)
                .toList();
    }

    public List<CreateMovieScreeningResponseDTO> findUpcoming() {
        return movieScreeningRepository.findByStartsAtAfterOrderByStartsAtAsc(OffsetDateTime.now()).stream()
                .map(CreateMovieScreeningResponseDTO::from)
                .toList();
    }

    public List<CreateMovieScreeningResponseDTO> findByMovie(UUID movieId) {
        movieService.getMovie(movieId);
        return movieScreeningRepository.findByMovieUuid(movieId).stream()
                .map(CreateMovieScreeningResponseDTO::from)
                .toList();
    }

    public CreateMovieScreeningResponseDTO findById(UUID uuid) {
        return CreateMovieScreeningResponseDTO.from(getScreening(uuid));
    }

    @Transactional
    public CreateMovieScreeningResponseDTO update(UUID uuid, CreateMovieScreeningDTO request) {
        MovieScreening screening = getScreening(uuid);
        Movie movie = movieService.getMovie(request.movieId());
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());

        OffsetDateTime startsAt = request.startsAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(movie.getRuntime() + TURNOVER_MINUTES);

        if (movieScreeningRepository.overlapsInRoom(room.getUuid(), startsAt, endsAt, uuid)) {
            throw new ConflictException(
                    "Room %s already has a screening overlapping %s - %s".formatted(room.getRoomName(), startsAt, endsAt));
        }

        screening.setMovie(movie);
        screening.setScreenRoom(room);
        screening.setStartsAt(startsAt);
        screening.setEndsAt(endsAt);
        screening.setBasePrice(request.basePrice());
        return CreateMovieScreeningResponseDTO.from(screening);
    }

    @Transactional
    public void delete(UUID uuid) {
        movieScreeningRepository.delete(getScreening(uuid));
    }

    MovieScreening getScreening(UUID uuid) {
        return movieScreeningRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Movie screening", uuid));
    }
}
