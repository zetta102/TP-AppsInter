package tp.appsinter.cine.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tp.appsinter.cine.shared.persistence.IdCount;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByScreenRoomUuid(UUID screenRoomUuid);

    List<Seat> findByUuidIn(Collection<UUID> uuids);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumn(UUID screenRoomUuid, String row, String column);

    boolean existsByScreenRoomUuidAndPositionRowAndPositionColumnAndUuidNot(
            UUID screenRoomUuid, String row, String column, UUID uuid);

    /**
     * Butacas por sala para un conjunto de salas, en una sola consulta.
     *
     * <p>Es la mitad «capacidad» del cálculo de disponibilidad de la cartelera, y la cantidad de
     * butacas del listado de salas. Una sala sin butacas cargadas no aparece en el resultado y cuenta
     * como cero.
     */
    @Query("""
            select new tp.appsinter.cine.shared.persistence.IdCount(s.screenRoom.uuid, count(s))
            from Seat s
            where s.screenRoom.uuid in :roomUuids
            group by s.screenRoom.uuid
            """)
    List<IdCount> countByScreenRoom(@Param("roomUuids") Collection<UUID> roomUuids);
}
