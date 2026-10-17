package tp.appsinter.cine.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByScreenRoomUuid(UUID screenRoomUuid);

    List<Seat> findByUuidIn(List<UUID> uuids);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumn(UUID screenRoomUuid, String row, String column);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumnAndUuidNot(
            UUID screenRoomUuid, String row, String column, UUID uuid);

    /**
     * Butacas por sala para un conjunto de salas, en una sola consulta.
     *
     * <p>Es la mitad «capacidad» del cálculo de disponibilidad de la cartelera. Una sala sin butacas
     * cargadas no aparece en el resultado y cuenta como cero.
     */
    @Query("""
            select s.screenRoom.uuid, count(s)
            from Seat s
            where s.screenRoom.uuid in :roomUuids
            group by s.screenRoom.uuid
            """)
    List<Object[]> countByScreenRoom(@Param("roomUuids") Collection<UUID> roomUuids);
}
