package tp.appsinter.cine.model.dto.request;

import org.springframework.format.annotation.DateTimeFormat;
import tp.appsinter.cine.model.utils.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criterios de búsqueda aceptados por los listados de reservas.
 *
 * <p>Spring enlaza este registro directamente desde los parámetros de consulta, así que la firma de
 * los controladores no crece un argumento por filtro. Todos los campos son opcionales; los nulos se
 * descartan al construir la consulta.
 *
 * <p>El significado de {@code from}/{@code to} depende del listado: sobre {@code /all} acotan la
 * fecha de compra y sobre el historial propio, el comienzo de la función. Es deliberado, porque cada
 * pantalla pregunta por una cosa distinta.
 */
public record ReservationFilterDTO(
        ReservationStatus status,
        UUID userId,
        String email,
        UUID screeningId,
        UUID movieId,
        Boolean upcomingOnly,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

    /**
     * {@code upcomingOnly} se declara envuelto y no como primitivo: el enlace por constructor recibe
     * nulo cuando el parámetro no viene, y un primitivo ahí falla con un {@code 400} que nadie pidió.
     */
    public ReservationFilterDTO {
        upcomingOnly = upcomingOnly != null && upcomingOnly;
    }

    /**
     * Filtro vacío, para los listados que no reciben criterios.
     */
    public static ReservationFilterDTO empty() {
        return new ReservationFilterDTO(null, null, null, null, null, false, null, null);
    }
}
