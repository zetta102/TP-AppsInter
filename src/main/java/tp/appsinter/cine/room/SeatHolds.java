package tp.appsinter.cine.room;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que el borrado de una butaca necesita saber de sus bloqueos.
 *
 * <p>Lo implementa {@code hold}, que depende de {@code room} y no al revés.
 */
public interface SeatHolds {

    /**
     * Indica si algún bloqueo vigente aparta la butaca: alguien la está comprando ahora mismo.
     */
    boolean anyLive(UUID seatUuid, OffsetDateTime now);
}
