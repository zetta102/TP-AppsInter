package tp.appsinter.cine.screening;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Cambió la disponibilidad de algunas butacas de una función: se bloquearon, se soltaron, vencieron,
 * se vendieron o se liberaron por una cancelación.
 *
 * <p>No dice cuál es el estado nuevo, porque depende de quién mire —los bloqueos propios se ven
 * disponibles—; quien lo escucha lo vuelve a calcular. Se publica dentro de la transacción que hizo
 * el cambio y sólo se atiende si ésta confirma.
 */
public record SeatsChangedEvent(UUID screeningId, Set<UUID> seatIds) {

    public SeatsChangedEvent(UUID screeningId, Collection<UUID> seatIds) {
        this(screeningId, Set.copyOf(seatIds));
    }
}
