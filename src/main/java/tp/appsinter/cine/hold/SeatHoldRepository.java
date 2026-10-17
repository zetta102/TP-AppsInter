package tp.appsinter.cine.hold;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Bloqueos temporales de butacas.
 *
 * <p>Todas las consultas filtran por vigencia ({@code releasedAt is null and expiresAt > :now}) en
 * lugar de confiar en que el barrido haya pasado: un bloqueo vencido que todavía tiene fila no debe
 * impedir una venta.
 */
@Repository
public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    /**
     * Butacas del conjunto pedido que ya están bloqueadas y vigentes por otra persona.
     *
     * @param excludedUserUuid dueño que no cuenta como conflicto, para que ampliar el propio
     *                         bloqueo no choque consigo mismo; {@code null} cuenta todos los
     *                         bloqueos, como los ve alguien anónimo
     */
    @Query("""
            select seat.uuid
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid = :screeningUuid
              and seat.uuid in :seatUuids
              and (:excludedUserUuid is null or h.user.uuid <> :excludedUserUuid)
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    List<UUID> findHeldSeatIdsAmong(@Param("screeningUuid") UUID screeningUuid,
                                    @Param("seatUuids") Collection<UUID> seatUuids,
                                    @Param("excludedUserUuid") @Nullable UUID excludedUserUuid,
                                    @Param("now") OffsetDateTime now);

    /**
     * Butacas de una función bloqueadas y vigentes por alguien distinto de quien mira el mapa.
     */
    @Query("""
            select seat.uuid
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid = :screeningUuid
              and (:viewerUuid is null or h.user.uuid <> :viewerUuid)
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    List<UUID> findHeldSeatIds(@Param("screeningUuid") UUID screeningUuid,
                               @Param("viewerUuid") @Nullable UUID viewerUuid,
                               @Param("now") OffsetDateTime now);

    /**
     * Butacas bloqueadas y vigentes por función para un conjunto de funciones, en una sola consulta.
     *
     * <p>Es la tercera pata de la disponibilidad de la cartelera, junto con la capacidad y las
     * entradas vendidas. Cuenta cada butaca una sola vez aunque figure en varios bloqueos, y descarta
     * las que ya están vendidas para no restarlas dos veces. Las funciones sin bloqueos no aparecen.
     *
     * @param viewerUuid quien mira; sus propios bloqueos no cuentan, o {@code null} para contar todos
     */
    @Query("""
            select h.movieScreening.uuid, count(distinct seat.uuid)
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid in :screeningUuids
              and (:viewerUuid is null or h.user.uuid <> :viewerUuid)
              and h.releasedAt is null
              and h.expiresAt > :now
              and not exists (
                  select t.uuid from Ticket t
                  where t.movieScreening = h.movieScreening
                    and t.seat = seat
                    and t.cancelledAt is null)
            group by h.movieScreening.uuid
            """)
    List<Object[]> countHeldByScreening(@Param("screeningUuids") Collection<UUID> screeningUuids,
                                        @Param("viewerUuid") @Nullable UUID viewerUuid,
                                        @Param("now") OffsetDateTime now);

    /**
     * Bloqueos vencidos que nadie soltó: el barrido avisa que sus butacas quedaron libres antes de
     * borrarlos.
     */
    @Query("select h from SeatHold h where h.expiresAt <= :now and h.releasedAt is null")
    List<SeatHold> findExpiredUnreleased(@Param("now") OffsetDateTime now);

    /**
     * Borra los bloqueos ya inertes. Es sólo higiene de la tabla.
     */
    @Modifying
    @Query("delete from SeatHold h where h.expiresAt <= :now or h.releasedAt is not null")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
