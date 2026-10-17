package tp.appsinter.cine.user;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que la baja de un usuario necesita saber de sus reservas.
 *
 * <p>Lo implementa {@code reservation}, que depende de {@code user} y no al revés.
 */
public interface UserReservations {

    /**
     * Indica si el usuario tiene reservas vigentes para funciones que todavía no terminaron.
     */
    boolean hasActive(UUID userUuid, OffsetDateTime now);
}
