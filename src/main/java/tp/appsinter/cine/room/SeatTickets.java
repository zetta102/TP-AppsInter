package tp.appsinter.cine.room;

import java.util.UUID;

/**
 * Lo que la edición de una butaca necesita saber de sus entradas.
 *
 * <p>Lo implementa {@code reservation}, que depende de {@code room} y no al revés.
 */
public interface SeatTickets {

    /**
     * Indica si la butaca figura en alguna entrada, vigente o cancelada: ambas son historial.
     */
    boolean anyFor(UUID seatUuid);
}
