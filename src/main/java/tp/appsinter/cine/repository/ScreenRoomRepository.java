package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.ScreenRoom;

import java.util.UUID;

@Repository
public interface ScreenRoomRepository extends JpaRepository<ScreenRoom, UUID> {
}
