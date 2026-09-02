package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Ticket;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByMovieScreeningUuid(UUID movieScreeningUuid);

    @Query("select t.seat.uuid from Ticket t where t.movieScreening.uuid = :screeningUuid")
    List<UUID> findBookedSeatIds(@Param("screeningUuid") UUID screeningUuid);

    boolean existsByMovieScreeningUuidAndSeatUuidIn(UUID movieScreeningUuid, List<UUID> seatUuids);
}
