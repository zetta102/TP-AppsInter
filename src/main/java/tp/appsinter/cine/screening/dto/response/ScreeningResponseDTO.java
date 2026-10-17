package tp.appsinter.cine.screening.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.screening.MovieScreening;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Función tal como aparece en la cartelera.
 *
 * <p>Los campos de disponibilidad son nulos cuando quien consulta pidió no calcularlos. Se distingue
 * «no calculado» de «cero butacas» a propósito: un cero mostrado por error sería un «Agotado» falso
 * en la grilla.
 *
 * <p>{@code availableSeats} es lo que quien consulta puede elegir ahora: descuenta lo vendido y lo
 * que otros clientes tienen bloqueado. {@code soldOut} sólo se enciende cuando está todo vendido; si
 * {@code availableSeats} es cero pero {@code soldOut} es falso, lo que queda está bloqueado y puede
 * liberarse en minutos.
 *
 * @param heldSeats butacas bloqueadas en este momento por otros clientes (sin contar las propias)
 * @param archived  si la función está archivada: ya no admite bloqueos ni compras
 * @param seatMap   mapa de butacas, sólo con {@code include=seatMap}; si no se pidió, el campo no
 *                  se envía
 */
@Schema(name = "Screening")
public record ScreeningResponseDTO(
        UUID uuid,
        UUID movieId,
        String movieName,
        UUID screenRoomId,
        String roomName,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal basePrice,
        @Nullable Integer totalSeats,
        @Nullable Integer availableSeats,
        @Nullable Integer heldSeats,
        @Nullable Boolean soldOut,
        boolean archived,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SeatMapResponseDTO seatMap
) {
    public static ScreeningResponseDTO from(MovieScreening screening) {
        return new ScreeningResponseDTO(
                screening.getUuid(),
                screening.getMovie().getUuid(),
                screening.getMovie().getMovieName(),
                screening.getScreenRoom().getUuid(),
                screening.getScreenRoom().getRoomName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getBasePrice(),
                null, null, null, null,
                screening.isArchived(),
                null);
    }

    /**
     * @param totalSeats  butacas de la sala
     * @param bookedSeats butacas ya vendidas
     * @param heldSeats   butacas sin vender bloqueadas por otros clientes
     */
    public static ScreeningResponseDTO from(MovieScreening screening,
                                                       int totalSeats,
                                                       int bookedSeats,
                                                       int heldSeats) {
        int unsold = Math.max(0, totalSeats - bookedSeats);
        int held = Math.min(heldSeats, unsold);
        return new ScreeningResponseDTO(
                screening.getUuid(),
                screening.getMovie().getUuid(),
                screening.getMovie().getMovieName(),
                screening.getScreenRoom().getUuid(),
                screening.getScreenRoom().getRoomName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getBasePrice(),
                totalSeats,
                unsold - held,
                held,
                unsold == 0,
                screening.isArchived(),
                null
        );
    }

    /**
     * La misma función con su mapa de butacas adjunto.
     */
    public ScreeningResponseDTO withSeatMap(SeatMapResponseDTO map) {
        return new ScreeningResponseDTO(uuid, movieId, movieName, screenRoomId, roomName, startsAt,
                endsAt, basePrice, totalSeats, availableSeats, heldSeats, soldOut, archived, map);
    }
}
