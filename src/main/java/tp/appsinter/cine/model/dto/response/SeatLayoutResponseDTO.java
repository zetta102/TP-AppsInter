package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.ScreenRoom;

import java.util.List;
import java.util.UUID;

/**
 * Grilla de butacas de una sala. {@code rows} y {@code columns} son las coordenadas distintas y
 * ordenadas naturalmente, de modo que el cliente puede dibujar la grilla sin deducir sus
 * dimensiones por su cuenta.
 */
public record SeatLayoutResponseDTO(
        UUID screenRoomId,
        String roomName,
        List<String> rows,
        List<String> columns,
        List<CreateSeatResponseDTO> seats
) {
    public static SeatLayoutResponseDTO of(ScreenRoom room,
                                           List<String> rows,
                                           List<String> columns,
                                           List<CreateSeatResponseDTO> seats) {
        return new SeatLayoutResponseDTO(room.getUuid(), room.getRoomName(), rows, columns, seats);
    }
}
