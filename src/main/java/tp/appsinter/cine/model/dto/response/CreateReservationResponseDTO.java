package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.model.entities.MovieScreening;
import tp.appsinter.cine.model.entities.Reservation;
import tp.appsinter.cine.model.utils.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reserva tal como la ve tanto su titular como la administración.
 *
 * <p>Incluye el contexto de la función —cuándo y en qué sala— para que la pantalla «mis reservas»
 * no tenga que pedir cada función por separado, y el titular para que la consola de administración
 * pueda identificar al cliente.
 *
 * <p>En una reserva cancelada, {@code totalPrice} es lo que se debe —cero— y
 * {@code originalTotalPrice} lo que se dejó de cobrar. Las entradas y las líneas de confitería se
 * devuelven igual: cancelar no borra la historia.
 */
@Schema(name = "Reservation")
public record CreateReservationResponseDTO(
        UUID uuid,
        ReservationUserResponseDTO user,
        UUID movieScreeningId,
        String movieName,
        OffsetDateTime screeningStartsAt,
        String roomName,
        ReservationStatus status,
        BigDecimal totalPrice,
        @Nullable BigDecimal originalTotalPrice,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime cancelledAt,
        List<TicketResponseDTO> tickets,
        List<ReservationItemResponseDTO> items
) {
    public static CreateReservationResponseDTO from(Reservation reservation) {
        MovieScreening screening = reservation.getMovieScreening();
        return new CreateReservationResponseDTO(
                reservation.getUuid(),
                ReservationUserResponseDTO.from(reservation.getUser()),
                screening.getUuid(),
                screening.getMovie().getMovieName(),
                screening.getStartsAt(),
                screening.getScreenRoom().getRoomName(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getOriginalTotalPrice(),
                reservation.getCreatedAt(),
                reservation.getCancelledAt(),
                reservation.getTickets().stream().map(TicketResponseDTO::from).toList(),
                reservation.getItems().stream().map(ReservationItemResponseDTO::from).toList()
        );
    }
}
