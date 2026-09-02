package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Seat;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByScreenRoomUuid(UUID screenRoomUuid);

    List<Seat> findByUuidIn(List<UUID> uuids);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumn(UUID screenRoomUuid, String row, String column);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumnAndUuidNot(
            UUID screenRoomUuid, String row, String column, UUID uuid);
}
