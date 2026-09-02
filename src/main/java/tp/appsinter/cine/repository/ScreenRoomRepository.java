package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.ScreenRoom;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScreenRoomRepository extends JpaRepository<ScreenRoom, UUID> {

    Optional<ScreenRoom> findByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCaseAndUuidNot(String roomName, UUID uuid);
}
