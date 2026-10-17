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
 */
public interface HeldSeats {

    /**
     * Cantidad de butacas bloqueadas por función, sin contar las de {@code viewerUuid} ni las que ya
     * están vendidas, para no restarlas dos veces. Una función sin bloqueos no figura en el mapa.
     */
    Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids, @Nullable UUID viewerUuid,
                                        OffsetDateTime now);

    List<UUID> findIds(UUID screeningUuid, @Nullable UUID viewerUuid, OffsetDateTime now);

    /**
     * Devuelve sólo las butacas del conjunto pedido que están bloqueadas por alguien distinto de
     * {@code excludedUserUuid}.
     */
    List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids, @Nullable UUID excludedUserUuid,
                            OffsetDateTime now);
}
