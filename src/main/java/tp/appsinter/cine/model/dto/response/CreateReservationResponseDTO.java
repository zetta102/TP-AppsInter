package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.Reservation;
import tp.appsinter.cine.model.utils.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateReservationResponseDTO(
        UUID uuid,
        UUID movieScreeningId,
        String movieName,
        ReservationStatus status,
        BigDecimal totalPrice,
        OffsetDateTime createdAt,
        List<TicketResponseDTO> tickets,
        List<ReservationItemResponseDTO> items
) {
    public static CreateReservationResponseDTO from(Reservation reservation) {
        return new CreateReservationResponseDTO(
                reservation.getUuid(),
                reservation.getMovieScreening().getUuid(),
                reservation.getMovieScreening().getMovie().getMovieName(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt(),
                reservation.getTickets().stream().map(TicketResponseDTO::from).toList(),
                reservation.getItems().stream().map(ReservationItemResponseDTO::from).toList()
        );
    }
}
