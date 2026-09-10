package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.SeatHold;

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
     *                         bloqueo no choque consigo mismo
     */
    @Query("""
            select seat.uuid
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid = :screeningUuid
              and seat.uuid in :seatUuids
              and h.user.uuid <> :excludedUserUuid
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    List<UUID> findHeldSeatIdsAmong(@Param("screeningUuid") UUID screeningUuid,
                                    @Param("seatUuids") Collection<UUID> seatUuids,
                                    @Param("excludedUserUuid") UUID excludedUserUuid,
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
                               @Param("viewerUuid") UUID viewerUuid,
                               @Param("now") OffsetDateTime now);

    /**
     * Borra los bloqueos ya inertes. Es sólo higiene de la tabla.
     */
    @Modifying
    @Query("delete from SeatHold h where h.expiresAt <= :now or h.releasedAt is not null")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
