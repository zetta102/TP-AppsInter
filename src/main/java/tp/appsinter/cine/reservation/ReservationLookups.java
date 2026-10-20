package tp.appsinter.cine.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.inventory.ProductSales;
import tp.appsinter.cine.room.SeatTickets;
import tp.appsinter.cine.screening.SoldSeats;
import tp.appsinter.cine.shared.persistence.IdCount;
import tp.appsinter.cine.user.UserReservations;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Responde a {@code user}, {@code room}, {@code screening} e {@code inventory} sobre reservas y
 * entradas, sin que esas funcionalidades tengan que conocer los repositorios de {@code reservation}.
 */
@Component
@RequiredArgsConstructor
class ReservationLookups implements SoldSeats, SeatTickets, UserReservations, ProductSales {

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids) {
        return IdCount.toMap(ticketRepository.countBookedByScreening(screeningUuids));
    }

    @Override
    public boolean anyActive(UUID screeningUuid) {
        return ticketRepository.existsByMovieScreeningUuidAndCancelledAtIsNull(screeningUuid);
    }

    @Override
    public boolean anyFor(UUID seatUuid) {
        return ticketRepository.existsBySeatUuid(seatUuid);
    }

    @Override
    public List<UUID> findIds(UUID screeningUuid) {
        return ticketRepository.findBookedSeatIds(screeningUuid);
    }

    @Override
    public List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids) {
        return ticketRepository.findBookedSeatIdsAmong(screeningUuid, seatUuids);
    }

    @Override
    public boolean hasActive(UUID userUuid, OffsetDateTime now) {
        return reservationRepository.existsActiveForUser(userUuid, now);
    }

    @Override
    public boolean hasSales(UUID inventoryUuid) {
        return reservationRepository.existsItemForProduct(inventoryUuid);
    }
}
