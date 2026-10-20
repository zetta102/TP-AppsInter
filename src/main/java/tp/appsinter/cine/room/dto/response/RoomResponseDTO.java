package tp.appsinter.cine.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomType;

import java.util.UUID;

/**
 * @param seatCount cantidad de butacas cargadas en la sala. Se recibe ya contada: recorrer
 *                  {@code getSeats()} cargaría todas las butacas de cada sala de un listado.
 */
@Schema(name = "Room")
public record RoomResponseDTO(
        UUID uuid,
        String roomName,
        ScreenRoomType screenRoomType,
        int seatCount,
        boolean archived
) {
    public static RoomResponseDTO from(ScreenRoom screenRoom, int seatCount) {
        return new RoomResponseDTO(
                screenRoom.getUuid(),
                screenRoom.getRoomName(),
                screenRoom.getScreenRoomType(),
                seatCount,
                screenRoom.isArchived()
        );
    }
}
