package tp.appsinter.cine.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.reservation.Ticket;
import tp.appsinter.cine.room.SeatType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param cancelledAt momento en que la entrada quedó liberada por una cancelación, o {@code null} si
 *                    sigue vigente
 */
@Schema(name = "Ticket")
public record TicketResponseDTO(
        UUID uuid,
        UUID seatId,
        String row,
        String column,
        SeatType seatType,
        BigDecimal price,
        @Nullable OffsetDateTime cancelledAt
) {
    public static TicketResponseDTO from(Ticket ticket) {
        return new TicketResponseDTO(
                ticket.getUuid(),
                ticket.getSeat().getUuid(),
                ticket.getSeat().getPosition().row(),
                ticket.getSeat().getPosition().column(),
                ticket.getSeatType(),
                ticket.getPrice(),
                ticket.getCancelledAt()
        );
    }
}
