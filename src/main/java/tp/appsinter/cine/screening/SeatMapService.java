package tp.appsinter.cine.screening;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatCoordinateComparator;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.screening.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.screening.dto.response.SeatMapResponseDTO;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mapa de butacas de una función: cuáles están libres, vendidas o bloqueadas por otro cliente.
 *
 * <p>Vive en {@code screening} porque describe una función. Las ventas y los bloqueos los aportan
 * {@link SoldSeats} y {@link HeldSeats}, que implementan {@code reservation} y {@code hold}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatMapService {

    private final MovieScreeningService movieScreeningService;
    private final SeatRepository seatRepository;
    private final SoldSeats soldSeats;
    private final HeldSeats heldSeats;
    private final Clock clock;

    /**
     * Mapa de butacas de una función.
     *
     * <p>Distingue tres situaciones: libre, vendida y bloqueada por otro cliente. La última no es
     * una venta, pero para quien mira el mapa se comporta como tal, y decirlo evita que elija una
     * butaca que casi con seguridad va a perder.
     *
     * @param viewerEmail quien consulta, o {@code null} si no está autenticado; sus propios
     *                    bloqueos no se marcan, porque para esa persona esas butacas están libres
     */
    public SeatMapResponseDTO findSeatMap(UUID screeningUuid, @Nullable String viewerEmail) {
        MovieScreening screening = movieScreeningService.getScreening(screeningUuid);
        Set<UUID> booked = new HashSet<>(soldSeats.findIds(screeningUuid));
        UUID viewerUuid = movieScreeningService.viewerUuid(viewerEmail);
        Set<UUID> held = new HashSet<>(heldSeats.findIds(screeningUuid, viewerUuid, OffsetDateTime.now(clock)));
        List<Seat> seats = seatRepository.findByScreenRoomUuid(screening.getScreenRoom().getUuid());

        return SeatMapResponseDTO.of(
                screening,
                SeatCoordinateComparator.distinctSorted(seats, seat -> seat.getPosition().row()),
                SeatCoordinateComparator.distinctSorted(seats, seat -> seat.getPosition().column()),
                seats.stream()
                        .map(seat -> SeatAvailabilityResponseDTO.of(
                                seat,
                                !booked.contains(seat.getUuid()) && !held.contains(seat.getUuid()),
                                // Vendida gana sobre bloqueada: un bloqueo que quedó vivo sobre una
                                // butaca ya vendida no debe mostrarla como «alguien la está comprando».
                                !booked.contains(seat.getUuid()) && held.contains(seat.getUuid())))
                        .toList());
    }
}
