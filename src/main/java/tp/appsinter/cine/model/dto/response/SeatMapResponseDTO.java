package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.MovieScreening;

import java.util.List;
import java.util.UUID;

/**
 * Grilla de butacas de una función, con la disponibilidad de cada una. Lleva las dimensiones de la
 * grilla para que el cliente no tenga que deducirlas de la lista de butacas.
 */
public record SeatMapResponseDTO(
        UUID screeningId,
        UUID screenRoomId,
        String roomName,
        List<String> rows,
        List<String> columns,
        List<SeatAvailabilityResponseDTO> seats
) {
    public static SeatMapResponseDTO of(MovieScreening screening,
                                        List<String> rows,
                                        List<String> columns,
                                        List<SeatAvailabilityResponseDTO> seats) {
        return new SeatMapResponseDTO(
                screening.getUuid(),
                screening.getScreenRoom().getUuid(),
                screening.getScreenRoom().getRoomName(),
                rows,
                columns,
                seats
        );
    }
}
