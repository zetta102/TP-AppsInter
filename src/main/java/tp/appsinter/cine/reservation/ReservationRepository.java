package tp.appsinter.cine.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    /**
     * Indica si el usuario tiene reservas vigentes: sin cancelar y para una función que todavía no
     * empezó. Es lo que impide darlo de baja y dejar butacas vendidas sin titular alcanzable.
     */
    @Query("""
            select count(r) > 0
              from Reservation r
             where r.user.uuid = :userUuid
               and r.status <> tp.appsinter.cine.reservation.ReservationStatus.CANCELLED
               and r.movieScreening.startsAt > :now
            """)
    boolean existsActiveForUser(@Param("userUuid") UUID userUuid, @Param("now") OffsetDateTime now);

    /**
     * Indica si algún producto de la confitería figura en una línea de reserva, vigente o cancelada.
     */
    @Query("select count(i) > 0 from ReservationItem i where i.inventory.uuid = :inventoryUuid")
    boolean existsItemForProduct(@Param("inventoryUuid") UUID inventoryUuid);
}
