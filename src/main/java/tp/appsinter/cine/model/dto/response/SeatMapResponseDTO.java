package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.MovieScreening;

import java.util.List;
import java.util.UUID;

/**
 * Seat grid for a screening, with per-seat availability. Carries the grid dimensions so the client
 * does not have to infer them from the seat list.
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
