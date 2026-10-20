package tp.appsinter.cine.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScreenRoomRepository extends JpaRepository<ScreenRoom, UUID> {

    boolean existsByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCaseAndUuidNot(String roomName, UUID uuid);

    /**
     * Salas vigentes; el listado completo, archivadas incluidas, es {@link #findAll(Pageable)}.
     */
    Page<ScreenRoom> findByArchivedAtIsNull(Pageable pageable);
}
