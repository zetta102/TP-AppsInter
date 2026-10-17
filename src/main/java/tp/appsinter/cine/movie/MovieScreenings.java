package tp.appsinter.cine.movie;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que el archivado de una película necesita saber de sus funciones.
 *
 * <p>Lo implementa {@code screening}, que depende de {@code movie} y no al revés.
 */
public interface MovieScreenings {

    /**
     * Indica si la película tiene funciones sin archivar que todavía no terminaron.
     */
    boolean hasUpcoming(UUID movieUuid, OffsetDateTime now);
}
