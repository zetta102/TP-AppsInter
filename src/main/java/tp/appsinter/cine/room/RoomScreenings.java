package tp.appsinter.cine.room;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que el archivado de una sala necesita saber de sus funciones.
 *
 * <p>Lo implementa {@code screening}, que depende de {@code room} y no al revés.
 */
public interface RoomScreenings {

    /**
     * Indica si la sala tiene funciones sin archivar que todavía no terminaron. El fin de una función
     * incluye el recambio de sala, así que una función cuenta hasta que la sala se libera.
     */
    boolean hasUpcoming(UUID roomUuid, OffsetDateTime now);
}
