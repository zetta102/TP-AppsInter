package tp.appsinter.cine.hold;

import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tp.appsinter.cine.shared.persistence.IdCount;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bloqueos temporales de butacas.
 *
 * <p>Las consultas de disponibilidad filtran por vigencia ({@code releasedAt is null and expiresAt >
 * :now}) en lugar de confiar en que el barrido haya pasado: un bloqueo vencido que todavía tiene fila
 * no debe impedir una venta. Las del barrido buscan justamente lo contrario, los que ya no rigen.
 */
public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    /**
     * Lee el bloqueo bloqueando su fila hasta el final de la transacción, para que una suelta o una
     * compra simultáneas no lo cambien entre la comprobación y la escritura.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h where h.uuid = :uuid")
    Optional<SeatHold> findByIdForUpdate(@Param("uuid") UUID uuid);

    /**
     * Función del bloqueo, sin cargar la entidad: así quien después lo lee con
     * {@link #findByIdForUpdate} obtiene su estado fresco y no una copia ya cargada.
     */
    @Query("select h.movieScreening.uuid from SeatHold h where h.uuid = :uuid")
    Optional<UUID> findScreeningUuid(@Param("uuid") UUID uuid);

    /**
     * Butacas del conjunto pedido que ya están bloqueadas y vigentes por otra persona.
     *
     * @param viewerUuid dueño que no cuenta como conflicto, para que ampliar el propio bloqueo no
     *                   choque consigo mismo; {@code null} cuenta todos los bloqueos, como los ve
     *                   alguien anónimo
     */
    @Query("""
            select seat.uuid
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid = :screeningUuid
              and seat.uuid in :seatUuids
              and (:viewerUuid is null or h.user.uuid <> :viewerUuid)
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    List<UUID> findHeldSeatIdsAmong(@Param("screeningUuid") UUID screeningUuid,
                                    @Param("seatUuids") Collection<UUID> seatUuids,
                                    @Param("viewerUuid") @Nullable UUID viewerUuid,
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
     * Bloqueos vigentes de una persona en una función que incluyen alguna de las butacas pedidas.
     * Son los que una compra de esas butacas deja sin objeto.
     */
    @Query("""
            select distinct h
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid = :screeningUuid
              and h.user.uuid = :userUuid
              and seat.uuid in :seatUuids
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    List<SeatHold> findLiveOwnedCovering(@Param("screeningUuid") UUID screeningUuid,
                                         @Param("userUuid") UUID userUuid,
                                         @Param("seatUuids") Collection<UUID> seatUuids,
                                         @Param("now") OffsetDateTime now);

    /**
     * Butacas bloqueadas y vigentes por función para un conjunto de funciones, en una sola consulta.
     *
     * <p>Es la tercera pata de la disponibilidad de la cartelera, junto con la capacidad y las
     * entradas vendidas. Cuenta cada butaca una sola vez aunque figure en varios bloqueos. Las
     * funciones sin bloqueos no aparecen.
     *
     * @param viewerUuid quien mira; sus propios bloqueos no cuentan, o {@code null} para contar todos
     */
    @Query("""
            select new tp.appsinter.cine.shared.persistence.IdCount(h.movieScreening.uuid, count(distinct seat.uuid))
            from SeatHold h
            join h.seats seat
            where h.movieScreening.uuid in :screeningUuids
              and (:viewerUuid is null or h.user.uuid <> :viewerUuid)
              and h.releasedAt is null
              and h.expiresAt > :now
            group by h.movieScreening.uuid
            """)
    List<IdCount> countHeldByScreening(@Param("screeningUuids") Collection<UUID> screeningUuids,
                                       @Param("viewerUuid") @Nullable UUID viewerUuid,
                                       @Param("now") OffsetDateTime now);

    /**
     * Indica si algún bloqueo vigente aparta la butaca.
     */
    @Query("""
            select count(h) > 0
            from SeatHold h
            join h.seats seat
            where seat.uuid = :seatUuid
              and h.releasedAt is null
              and h.expiresAt > :now
            """)
    boolean existsLiveContaining(@Param("seatUuid") UUID seatUuid, @Param("now") OffsetDateTime now);

    /**
     * Bloqueos vencidos que nadie soltó: el barrido avisa que sus butacas quedaron libres antes de
     * borrarlos.
     */
    @Query("select h from SeatHold h where h.expiresAt <= :now and h.releasedAt is null")
    List<SeatHold> findExpiredUnreleased(@Param("now") OffsetDateTime now);

    /**
     * Borra los bloqueos que ya no rigen, vencidos o soltados. Es sólo higiene de la tabla.
     */
    @Modifying
    @Query("delete from SeatHold h where h.expiresAt <= :now or h.releasedAt is not null")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
