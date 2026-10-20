package tp.appsinter.cine.reservation.dto.request;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import tp.appsinter.cine.reservation.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criterios de búsqueda del historial propio.
 *
 * <p>Es {@link ReservationFilterDTO} sin los filtros por titular, que en el historial propio no
 * tienen sentido: el titular es siempre quien consulta. Aquí {@code from}/{@code to} acotan el
 * comienzo de la función, porque un cliente pregunta por cuándo tiene que ir al cine.
 */
public record MyReservationFilterDTO(
        @Nullable ReservationStatus status,
        @Nullable UUID screeningId,
        @Nullable UUID movieId,
        @Nullable Boolean upcomingOnly,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime to) {

    /**
     * {@code upcomingOnly} se declara envuelto y no como primitivo: el enlace por constructor recibe
     * nulo cuando el parámetro no viene, y un primitivo ahí falla con un {@code 400} que nadie pidió.
     */
    public boolean upcoming() {
        return Boolean.TRUE.equals(upcomingOnly);
    }
}
