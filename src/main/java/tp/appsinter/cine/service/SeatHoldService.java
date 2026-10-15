package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.config.SeatHoldProperties;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateSeatHoldDTO;
import tp.appsinter.cine.model.dto.response.SeatHoldResponseDTO;
import tp.appsinter.cine.model.entities.MovieScreening;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.entities.SeatHold;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.repository.SeatHoldRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reserva temporal de butacas mientras dura una compra.
 *
 * <p>Un bloqueo no vende nada: sólo aparta unas butacas por unos minutos para que elegir la
 * confitería y pagar deje de ser una carrera contra los demás clientes. La venta sigue decidiéndose
 * en el índice único de {@code ticket}, que es la única barrera real contra la doble venta.
 *
 * <p>La vigencia se calcula siempre comparando contra {@code expiresAt}. El barrido programado sólo
 * borra filas inertes; si se atrasa, no cambia lo que está disponible.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SeatHoldService {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final MovieScreeningService movieScreeningService;
    private final UserService userService;
    private final SeatHoldProperties properties;

    @Transactional
    public SeatHoldResponseDTO create(UUID screeningUuid, String userEmail, CreateSeatHoldDTO request) {
        User user = userService.getByEmail(userEmail);
        // Sin el bloqueo, dos clientes podrían pasar a la vez la comprobación de abajo y retener la
        // misma butaca: ninguno de los dos podría comprarla hasta que venciera el otro bloqueo.
        MovieScreening screening = movieScreeningService.lockScreening(screeningUuid);
        OffsetDateTime now = OffsetDateTime.now();

        if (screening.getStartsAt().isBefore(now)) {
            throw BusinessRuleException.screeningStarted(screening.getUuid(), screening.getStartsAt());
        }

        List<Seat> seats = seatRepository.findByUuidIn(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new NotFoundException("One or more requested seats do not exist", "Seat");
        }

        UUID roomUuid = screening.getScreenRoom().getUuid();
        if (!seats.stream().allMatch(seat -> seat.getScreenRoom().getUuid().equals(roomUuid))) {
            throw BusinessRuleException.seatsWrongRoom(roomUuid, request.seatIds());
        }

        List<UUID> sold = ticketRepository.findBookedSeatIdsAmong(screening.getUuid(), request.seatIds());
        if (!sold.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are already booked for this screening", sold);
        }

        List<UUID> held = seatHoldRepository.findHeldSeatIdsAmong(
                screening.getUuid(), request.seatIds(), user.getUuid(), now);
        if (!held.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are being bought by another customer right now", held);
        }

        SeatHold hold = SeatHold.builder()
                .movieScreening(screening)
                .user(user)
                .seats(new ArrayList<>(seats))
                .createdAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        return SeatHoldResponseDTO.from(seatHoldRepository.save(hold), now);
    }

    /**
     * Un bloqueo vencido o soltado se comporta como inexistente: para quien lo consulta ya no
     * aparta nada, y no hay razón para distinguirlo de uno que nunca existió.
     */
    public SeatHoldResponseDTO findById(UUID uuid, String userEmail) {
        OffsetDateTime now = OffsetDateTime.now();
        return SeatHoldResponseDTO.from(getLiveHold(uuid, userEmail, now), now);
    }

    /**
     * Suelta un bloqueo antes de tiempo. Es idempotente: soltar algo ya soltado o ya vencido no es
     * un error, porque el efecto buscado —que esas butacas estén libres— ya se cumplió.
     */
    @Transactional
    public void release(UUID uuid, String userEmail) {
        seatHoldRepository.findById(uuid)
                .filter(hold -> hold.getUser().getEmail().equalsIgnoreCase(userEmail))
                .filter(hold -> hold.getReleasedAt() == null)
                .ifPresent(hold -> hold.setReleasedAt(OffsetDateTime.now()));
    }

    /**
     * Consume un bloqueo al confirmarse la compra que lo motivó.
     *
     * <p>Exige que cubra exactamente las butacas que se están comprando: un bloqueo que no las cubre
     * no es el de esta compra, y aceptarlo dejaría butacas apartadas sin dueño.
     */
    @Transactional
    public void consume(UUID uuid, String userEmail, UUID screeningUuid, Collection<UUID> seatIds) {
        OffsetDateTime now = OffsetDateTime.now();
        SeatHold hold = getLiveHold(uuid, userEmail, now);

        if (!hold.getMovieScreening().getUuid().equals(screeningUuid)) {
            throw new NotFoundException("Seat hold does not belong to this screening", "Seat hold");
        }
        Set<UUID> heldSeats = hold.getSeats().stream().map(Seat::getUuid).collect(Collectors.toSet());
        if (!heldSeats.containsAll(seatIds)) {
            throw ConflictException.seatsUnavailable(
                    "The hold does not cover every requested seat", seatIds);
        }
        hold.setReleasedAt(now);
    }

    /**
     * Butacas que otro cliente está comprando en este momento, para que el mapa pueda distinguir
     * «vendida» de «alguien la está comprando».
     *
     * @param viewerUuid quien mira; sus propios bloqueos no se marcan, porque para esa persona esas
     *                   butacas sí están disponibles
     */
    public List<UUID> findHeldSeatIds(UUID screeningUuid, UUID viewerUuid) {
        return seatHoldRepository.findHeldSeatIds(screeningUuid, viewerUuid, OffsetDateTime.now());
    }

    /**
     * Rechaza la compra si alguna butaca está bloqueada y vigente por otra persona.
     *
     * <p>Sin esta comprobación el bloqueo no serviría de nada: cualquiera podría comprar por encima
     * de él.
     */
    public void requireNotHeldByOthers(UUID screeningUuid, Collection<UUID> seatIds, UUID buyerUuid) {
        List<UUID> held = seatHoldRepository.findHeldSeatIdsAmong(
                screeningUuid, seatIds, buyerUuid, OffsetDateTime.now());
        if (!held.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are being bought by another customer right now", held);
        }
    }

    /**
     * Limpieza periódica de bloqueos ya inertes.
     */
    @Scheduled(fixedDelayString = "${cine.seat-hold.sweep-delay}")
    @Transactional
    public void sweepExpired() {
        int removed = seatHoldRepository.deleteExpired(OffsetDateTime.now());
        if (removed > 0) {
            log.debug("Swept {} expired seat hold(s)", removed);
        }
    }

    private SeatHold getLiveHold(UUID uuid, String userEmail, OffsetDateTime now) {
        return seatHoldRepository.findById(uuid)
                .filter(hold -> hold.getUser().getEmail().equalsIgnoreCase(userEmail))
                .filter(hold -> hold.isLive(now))
                .orElseThrow(() -> NotFoundException.of("Seat hold", uuid));
    }
}
