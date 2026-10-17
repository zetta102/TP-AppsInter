package tp.appsinter.cine.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.room.SeatTickets;
import tp.appsinter.cine.screening.SoldSeats;
import tp.appsinter.cine.user.UserReservations;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Responde a {@code user}, {@code room} y {@code screening} sobre reservas y entradas, sin que esas
 * funcionalidades tengan que conocer los repositorios de {@code reservation}.
 */
@Component
@RequiredArgsConstructor
class ReservationLookups implements SoldSeats, SeatTickets, UserReservations {

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids) {
        return ticketRepository.countBookedByScreening(screeningUuids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
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
    public List<UUID> findIdsAmong(UUID screeningUuid, List<UUID> seatUuids) {
        return ticketRepository.findBookedSeatIdsAmong(screeningUuid, seatUuids);
    }

    @Override
    public boolean hasActive(UUID userUuid, OffsetDateTime now) {
        return reservationRepository.existsActiveForUser(userUuid, now);
    }
}
