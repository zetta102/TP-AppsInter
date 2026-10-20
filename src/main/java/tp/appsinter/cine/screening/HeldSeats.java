package tp.appsinter.cine.screening;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Butacas bloqueadas de una función por bloqueos vigentes.
 *
 * <p>Lo implementa {@code hold}, que depende de {@code screening} y no al revés. Los bloqueos del
 * propio espectador nunca cuentan: para él esas butacas siguen libres.
 *
 * <p>Una butaca bloqueada nunca está vendida a la vez: sólo su dueño puede comprarla, y la compra
 * suelta los bloqueos de quien compra sobre esas butacas. Por eso las cuentas de vendidas y
 * bloqueadas se pueden restar de la capacidad sin descontar nada dos veces.
 */
public interface HeldSeats {

    /**
     * Cantidad de butacas bloqueadas por función, sin contar las de {@code viewerUuid}. Una función
     * sin bloqueos no figura en el mapa.
     */
    Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids, @Nullable UUID viewerUuid,
                                        OffsetDateTime now);

    /**
     * Todas las butacas de la función bloqueadas por alguien distinto de {@code viewerUuid}.
     */
    List<UUID> findIds(UUID screeningUuid, @Nullable UUID viewerUuid, OffsetDateTime now);

    /**
     * Devuelve sólo las butacas del conjunto pedido que están bloqueadas por alguien distinto de
     * {@code viewerUuid}.
     */
    List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids, @Nullable UUID viewerUuid,
                            OffsetDateTime now);
}
