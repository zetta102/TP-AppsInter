package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.MovieScreening;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateMovieScreeningResponseDTO(
        UUID uuid,
        UUID movieId,
        String movieName,
        UUID screenRoomId,
        String roomName,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal basePrice
) {
    public static CreateMovieScreeningResponseDTO from(MovieScreening screening) {
        return new CreateMovieScreeningResponseDTO(
                screening.getUuid(),
                screening.getMovie().getUuid(),
                screening.getMovie().getMovieName(),
                screening.getScreenRoom().getUuid(),
                screening.getScreenRoom().getRoomName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getBasePrice()
        );
    }
}
