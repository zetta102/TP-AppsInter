package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.MovieScreening;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Función tal como aparece en la cartelera.
 *
 * <p>Los tres campos de disponibilidad son nulos cuando quien consulta pidió no calcularlos. Se
 * distingue «no calculado» de «cero butacas» a propósito: un cero mostrado por error sería un
 * «Agotado» falso en la grilla.
 */
public record CreateMovieScreeningResponseDTO(
        UUID uuid,
        UUID movieId,
        String movieName,
        UUID screenRoomId,
        String roomName,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal basePrice,
        Integer totalSeats,
        Integer availableSeats,
        Boolean soldOut
) {
    public static CreateMovieScreeningResponseDTO from(MovieScreening screening) {
        return from(screening, null, null);
    }

    /**
     * @param totalSeats butacas de la sala, o {@code null} si no se pidió la disponibilidad
     * @param bookedSeats butacas ya vendidas, ignorado cuando {@code totalSeats} es nulo
     */
    public static CreateMovieScreeningResponseDTO from(MovieScreening screening,
                                                       Integer totalSeats,
                                                       Integer bookedSeats) {
        Integer available = totalSeats == null
                ? null
                : Math.max(0, totalSeats - (bookedSeats == null ? 0 : bookedSeats));
        return new CreateMovieScreeningResponseDTO(
                screening.getUuid(),
                screening.getMovie().getUuid(),
                screening.getMovie().getMovieName(),
                screening.getScreenRoom().getUuid(),
                screening.getScreenRoom().getRoomName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getBasePrice(),
                totalSeats,
                available,
                available == null ? null : available == 0
        );
    }
}
