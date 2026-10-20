package tp.appsinter.cine.movie;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que archivar una película, o cambiarle la duración, necesita saber de sus funciones.
 *
 * <p>Lo implementa {@code screening}, que depende de {@code movie} y no al revés.
 */
public interface MovieScreenings {

    /**
     * Indica si la película tiene funciones sin archivar que todavía no terminaron. El fin de una
     * función incluye el recambio de sala, así que una función cuenta hasta que la sala se libera.
     */
    boolean hasUpcoming(UUID movieUuid, OffsetDateTime now);
}
