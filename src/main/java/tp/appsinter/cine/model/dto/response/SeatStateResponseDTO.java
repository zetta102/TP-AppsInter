package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Estado nuevo de una butaca, tal como lo envía el canal {@code GET /api/screenings/{id}/seats/stream}.
 *
 * <p>Usa la misma semántica que {@link SeatAvailabilityResponseDTO} en el mapa de butacas:
 * {@code available} y {@code held} falsos significa vendida, y los bloqueos propios de quien mira
 * cuentan como disponibles.
 */
@Schema(name = "SeatState")
public record SeatStateResponseDTO(UUID seatId, boolean available, boolean held) {
}
