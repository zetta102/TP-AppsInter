package tp.appsinter.cine.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Las entradas son la fuente de verdad sobre la ocupación de butacas. Todas las consultas de
 * disponibilidad excluyen las entradas liberadas por una cancelación.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Indica si la función tiene alguna entrada vendida y sin cancelar.
     */
    boolean existsByMovieScreeningUuidAndCancelledAtIsNull(UUID movieScreeningUuid);

    /**
     * Indica si la butaca figura en alguna entrada, vigente o cancelada: ambas son historial.
     */
    boolean existsBySeatUuid(UUID seatUuid);

    @Query("""
            select t.seat.uuid from Ticket t
            where t.movieScreening.uuid = :screeningUuid and t.cancelledAt is null
            """)
    List<UUID> findBookedSeatIds(@Param("screeningUuid") UUID screeningUuid);

    /**
     * Devuelve sólo las butacas del conjunto pedido que ya están vendidas, para que el error de
     * conflicto pueda nombrarlas en su contexto.
     */
    @Query("""
            select t.seat.uuid from Ticket t
            where t.movieScreening.uuid = :screeningUuid
              and t.seat.uuid in :seatUuids
              and t.cancelledAt is null
            """)
    List<UUID> findBookedSeatIdsAmong(@Param("screeningUuid") UUID screeningUuid,
                                      @Param("seatUuids") List<UUID> seatUuids);

    /**
     * Entradas vendidas por función para un conjunto de funciones, en una sola consulta.
     *
     * <p>Una grilla de cartelera necesita el dato para cada tarjeta; resolverlo fila por fila sería
     * una consulta por función. Las funciones sin ninguna venta no aparecen en el resultado y deben
     * tratarse como cero.
     */
    @Query("""
            select t.movieScreening.uuid, count(t)
            from Ticket t
            where t.movieScreening.uuid in :screeningUuids and t.cancelledAt is null
            group by t.movieScreening.uuid
            """)
    List<Object[]> countBookedByScreening(@Param("screeningUuids") Collection<UUID> screeningUuids);
}
