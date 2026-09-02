package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Reservation;
import tp.appsinter.cine.model.utils.ReservationStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserUuid(UUID userUuid);

    List<Reservation> findByUserEmailIgnoreCase(String email);

    Page<Reservation> findByUserEmailIgnoreCase(String email, Pageable pageable);

    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    List<Reservation> findByMovieScreeningUuid(UUID movieScreeningUuid);
}
