package tp.appsinter.cine.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScreenRoomRepository extends JpaRepository<ScreenRoom, UUID> {

    Optional<ScreenRoom> findByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCaseAndUuidNot(String roomName, UUID uuid);

    /**
     * Salas vigentes; el listado completo, archivadas incluidas, es {@link #findAll(Pageable)}.
     */
    Page<ScreenRoom> findByArchivedAtIsNull(Pageable pageable);
}
