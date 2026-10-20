package tp.appsinter.cine.screening;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Butacas vendidas de una función: las que tienen una entrada vigente.
 *
 * <p>Lo implementa {@code reservation}, que depende de {@code screening} y no al revés. Lo usan la
 * disponibilidad de las funciones, el mapa y el canal de butacas, y los bloqueos.
 */
public interface SoldSeats {

    /**
     * Cantidad de butacas vendidas por función. Una función sin ventas no figura en el mapa.
     */
    Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids);

    /**
     * Indica si la función tiene al menos una entrada vigente.
     */
    boolean anyActive(UUID screeningUuid);

    /**
     * Todas las butacas vendidas de la función.
     */
    List<UUID> findIds(UUID screeningUuid);

    /**
     * Devuelve sólo las butacas del conjunto pedido que ya están vendidas.
     */
    List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids);
}
