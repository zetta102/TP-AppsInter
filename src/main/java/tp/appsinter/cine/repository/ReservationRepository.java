package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Reservation;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
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
               and r.cancelledAt is null
               and r.movieScreening.startsAt > :now
            """)
    boolean existsActiveForUser(@Param("userUuid") UUID userUuid, @Param("now") OffsetDateTime now);
}
