package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.Ticket;
import tp.appsinter.cine.model.utils.SeatType;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "Ticket")
public record TicketResponseDTO(
        UUID uuid,
        UUID seatId,
        String row,
        String column,
        SeatType seatType,
        BigDecimal price
) {
    public static TicketResponseDTO from(Ticket ticket) {
        return new TicketResponseDTO(
                ticket.getUuid(),
                ticket.getSeat().getUuid(),
                ticket.getSeat().getPosition().row(),
                ticket.getSeat().getPosition().column(),
                ticket.getSeatType(),
                ticket.getPrice()
        );
    }
}
