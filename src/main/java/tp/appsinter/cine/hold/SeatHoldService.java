package tp.appsinter.cine.hold;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.hold.dto.request.SeatHoldRequestDTO;
import tp.appsinter.cine.hold.dto.response.SeatHoldResponseDTO;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningService;
import tp.appsinter.cine.screening.SeatsChangedEvent;
import tp.appsinter.cine.screening.SoldSeats;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final SoldSeats soldSeats;
    private final MovieScreeningService movieScreeningService;
    private final UserService userService;
    private final SeatHoldProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public SeatHoldResponseDTO create(UUID screeningUuid, String userEmail, SeatHoldRequestDTO request) {
        User user = userService.getByEmail(userEmail);
        // Sin el bloqueo, dos clientes podrían pasar a la vez la comprobación de abajo y retener la
        // misma butaca: ninguno de los dos podría comprarla hasta que venciera el otro bloqueo.
        MovieScreening screening = movieScreeningService.lockScreening(screeningUuid);
        OffsetDateTime now = OffsetDateTime.now();
        List<Seat> seats = requireHoldable(screening, request.seatIds(), user, now);

        SeatHold hold = SeatHold.builder()
                .movieScreening(screening)
                .user(user)
                .seats(new ArrayList<>(seats))
                .createdAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        SeatHold saved = seatHoldRepository.save(hold);
        publishChanged(screening.getUuid(), request.seatIds());
        return SeatHoldResponseDTO.from(saved, now);
    }

    /**
     * Reemplaza de una vez el conjunto de butacas de un bloqueo vigente propio.
     *
     * <p>Es la alternativa a soltar el bloqueo y crear otro, que deja las butacas libres por un
     * instante y otro cliente puede ganarlas en ese hueco. Las validaciones son las mismas que al
     * crear, y el bloqueo conserva su {@code expiresAt}: cambiar de butacas no lo extiende.
     */
    @Transactional
    public SeatHoldResponseDTO replace(UUID uuid, String userEmail, SeatHoldRequestDTO request) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        UUID screeningUuid = getLiveHold(uuid, userEmail, requestedAt).getMovieScreening().getUuid();
        // Mismo orden de bloqueo que crear y comprar: primero la función, después la comprobación.
        MovieScreening screening = movieScreeningService.lockScreening(screeningUuid);
        OffsetDateTime now = OffsetDateTime.now();
        SeatHold hold = getLiveHold(uuid, userEmail, now);
        List<Seat> seats = requireHoldable(screening, request.seatIds(), hold.getUser(), now);

        Set<UUID> changed = new HashSet<>(request.seatIds());
        hold.getSeats().forEach(seat -> changed.add(seat.getUuid()));
        hold.getSeats().clear();
        hold.getSeats().addAll(seats);
        seatHoldRepository.flush();
        publishChanged(screening.getUuid(), changed);
        return SeatHoldResponseDTO.from(hold, now);
    }

    /**
     * Reglas comunes a crear y reemplazar un bloqueo, de la más barata a la más cara: función vigente
     * y no empezada, butacas existentes y de la sala, ni vendidas ni bloqueadas por otro cliente.
     */
    private List<Seat> requireHoldable(MovieScreening screening,
                                       List<UUID> seatIds,
                                       User holder,
                                       OffsetDateTime now) {
        MovieScreeningService.requireActive(screening);
        if (screening.getStartsAt().isBefore(now)) {
            throw BusinessRuleException.screeningStarted(screening.getUuid(), screening.getStartsAt());
        }

        List<Seat> seats = seatRepository.findByUuidIn(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new NotFoundException("One or more requested seats do not exist", "Seat");
        }

        UUID roomUuid = screening.getScreenRoom().getUuid();
        if (!seats.stream().allMatch(seat -> seat.getScreenRoom().getUuid().equals(roomUuid))) {
            throw BusinessRuleException.seatsWrongRoom(roomUuid, seatIds);
        }

        List<UUID> sold = soldSeats.findIdsAmong(screening.getUuid(), seatIds);
        if (!sold.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are already booked for this screening", sold);
        }

        List<UUID> held = seatHoldRepository.findHeldSeatIdsAmong(
                screening.getUuid(), seatIds, holder.getUuid(), now);
        if (!held.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are being bought by another customer right now", held);
        }
        return seats;
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
                .ifPresent(hold -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    boolean wasLive = hold.isLive(now);
                    hold.setReleasedAt(now);
                    // Uno ya vencido no apartaba nada: soltarlo no cambia lo que ven los demás.
                    if (wasLive) {
                        publishChanged(hold.getMovieScreening().getUuid(), seatIdsOf(hold));
                    }
                });
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
        requireUsable(uuid, userEmail, screeningUuid, seatIds, now).setReleasedAt(now);
    }

    /**
     * Comprueba, sin consumirlo, que el bloqueo serviría para esta compra. Lo usa el presupuesto,
     * para que un {@code holdId} vencido o ajeno falle ya al presupuestar y no recién al confirmar.
     */
    public void requireUsable(UUID uuid, String userEmail, UUID screeningUuid, Collection<UUID> seatIds) {
        requireUsable(uuid, userEmail, screeningUuid, seatIds, OffsetDateTime.now());
    }

    private SeatHold requireUsable(UUID uuid,
                                   String userEmail,
                                   UUID screeningUuid,
                                   Collection<UUID> seatIds,
                                   OffsetDateTime now) {
        SeatHold hold = getLiveHold(uuid, userEmail, now);
        if (!hold.getMovieScreening().getUuid().equals(screeningUuid)) {
            throw new NotFoundException("Seat hold does not belong to this screening", "Seat hold");
        }
        Set<UUID> heldSeats = hold.getSeats().stream().map(Seat::getUuid).collect(Collectors.toSet());
        if (!heldSeats.containsAll(seatIds)) {
            throw ConflictException.seatsUnavailable(
                    "The hold does not cover every requested seat", seatIds);
        }
        return hold;
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
        OffsetDateTime now = OffsetDateTime.now();
        // Los vencidos sin soltar no avisaron a nadie al vencer: el aviso sale acá, con a lo sumo un
        // período de barrido de demora. Los soltados ya avisaron al soltarse.
        Map<UUID, Set<UUID>> expiredSeats = new HashMap<>();
        for (SeatHold hold : seatHoldRepository.findExpiredUnreleased(now)) {
            expiredSeats.computeIfAbsent(hold.getMovieScreening().getUuid(), key -> new HashSet<>())
                    .addAll(seatIdsOf(hold));
        }
        int removed = seatHoldRepository.deleteExpired(now);
        expiredSeats.forEach(this::publishChanged);
        if (removed > 0) {
            log.debug("Swept {} expired seat hold(s)", removed);
        }
    }

    private void publishChanged(UUID screeningUuid, Collection<UUID> seatIds) {
        if (!seatIds.isEmpty()) {
            events.publishEvent(new SeatsChangedEvent(screeningUuid, seatIds));
        }
    }

    private static Set<UUID> seatIdsOf(SeatHold hold) {
        return hold.getSeats().stream().map(Seat::getUuid).collect(Collectors.toSet());
    }

    private SeatHold getLiveHold(UUID uuid, String userEmail, OffsetDateTime now) {
        return seatHoldRepository.findById(uuid)
                .filter(hold -> hold.getUser().getEmail().equalsIgnoreCase(userEmail))
                .filter(hold -> hold.isLive(now))
                .orElseThrow(() -> NotFoundException.of("Seat hold", uuid));
    }
}
