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
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningService;
import tp.appsinter.cine.screening.SeatsChangedEvent;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserService;

import java.time.Clock;
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
    private final MovieScreeningService movieScreeningService;
    private final UserService userService;
    private final SeatHoldProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public SeatHoldResponseDTO create(UUID screeningUuid, String userEmail, SeatHoldRequestDTO request) {
        User user = userService.getByEmail(userEmail);
        // Sin el bloqueo, dos clientes podrían pasar a la vez la comprobación de abajo y retener la
        // misma butaca: ninguno de los dos podría comprarla hasta que venciera el otro bloqueo.
        MovieScreening screening = movieScreeningService.lockScreening(screeningUuid);
        OffsetDateTime now = OffsetDateTime.now(clock);
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
        UUID screeningUuid = seatHoldRepository.findScreeningUuid(uuid)
                .orElseThrow(() -> NotFoundException.of("Seat hold", uuid));
        // Mismo orden de bloqueo que crear y comprar: primero la función, después el bloqueo, que se
        // lee recién ahora para ver una suelta o una compra que haya terminado mientras tanto.
        MovieScreening screening = movieScreeningService.lockScreening(screeningUuid);
        OffsetDateTime now = OffsetDateTime.now(clock);
        SeatHold hold = seatHoldRepository.findByIdForUpdate(uuid)
                .filter(candidate -> isOwnedLive(candidate, userEmail, now))
                .orElseThrow(() -> NotFoundException.of("Seat hold", uuid));
        List<Seat> seats = requireHoldable(screening, request.seatIds(), hold.getUser(), now);

        Set<UUID> changed = new HashSet<>(request.seatIds());
        changed.addAll(seatIdsOf(hold));
        hold.getSeats().clear();
        hold.getSeats().addAll(seats);
        seatHoldRepository.flush();
        publishChanged(screening.getUuid(), changed);
        return SeatHoldResponseDTO.from(hold, now);
    }

    /**
     * Reglas de crear y reemplazar un bloqueo: las de cualquier ocupación de butacas, más que
     * ninguna esté bloqueada por otro cliente.
     */
    private List<Seat> requireHoldable(MovieScreening screening,
                                       List<UUID> seatIds,
                                       User holder,
                                       OffsetDateTime now) {
        List<Seat> seats = movieScreeningService.requireBookableSeats(screening, seatIds, now);
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
        OffsetDateTime now = OffsetDateTime.now(clock);
        return SeatHoldResponseDTO.from(getLiveHold(uuid, userEmail, now), now);
    }

    /**
     * Suelta un bloqueo antes de tiempo. Es idempotente: soltar algo ya soltado o ya vencido no es
     * un error, porque el efecto buscado —que esas butacas estén libres— ya se cumplió.
     *
     * <p>El aviso sale aunque el bloqueo ya hubiera vencido: el barrido sólo avisa de los vencidos
     * que nadie soltó, así que de otro modo nadie anunciaría esas butacas.
     */
    @Transactional
    public void release(UUID uuid, String userEmail) {
        seatHoldRepository.findById(uuid)
                .filter(hold -> hold.getUser().getEmail().equalsIgnoreCase(userEmail))
                .filter(hold -> hold.getReleasedAt() == null)
                .ifPresent(hold -> {
                    hold.setReleasedAt(OffsetDateTime.now(clock));
                    publishChanged(hold.getMovieScreening().getUuid(), seatIdsOf(hold));
                });
    }

    /**
     * Comprueba, sin consumirlo, que el bloqueo serviría para esta compra: que es propio, vigente,
     * de esta función y cubre todas las butacas pedidas. Lo usa el presupuesto, para que un
     * {@code holdId} vencido o ajeno falle ya al presupuestar y no recién al confirmar.
     */
    public void requireUsable(UUID uuid, String userEmail, UUID screeningUuid, Collection<UUID> seatIds) {
        SeatHold hold = getLiveHold(uuid, userEmail, OffsetDateTime.now(clock));
        if (!hold.getMovieScreening().getUuid().equals(screeningUuid)) {
            throw NotFoundException.withMessage("Seat hold", "Seat hold does not belong to this screening");
        }
        if (!seatIdsOf(hold).containsAll(seatIds)) {
            throw ConflictException.seatsUnavailable("The hold does not cover every requested seat", seatIds);
        }
    }

    /**
     * Suelta los bloqueos que una compra deja sin objeto: los vigentes de quien compra en esa
     * función que incluyen alguna de las butacas compradas, sea o no el que indicó la compra.
     *
     * <p>Si un bloqueo apartaba más butacas de las que se compraron, las sobrantes quedan libres.
     * Por eso se devuelven todas las butacas de los bloqueos soltados, para avisar de ellas junto con
     * las vendidas. Así una butaca nunca queda a la vez vendida y bloqueada.
     *
     * <p>El bloqueo que indicó la compra, si indicó uno, ya pasó por {@link #requireUsable}.
     *
     * @return las butacas que apartaban los bloqueos soltados
     */
    @Transactional
    public Set<UUID> releaseForPurchase(User buyer, UUID screeningUuid, Collection<UUID> seatIds) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Set<UUID> freed = new HashSet<>();
        for (SeatHold hold : seatHoldRepository.findLiveOwnedCovering(screeningUuid, buyer.getUuid(), seatIds, now)) {
            hold.setReleasedAt(now);
            freed.addAll(seatIdsOf(hold));
        }
        return freed;
    }

    /**
     * Rechaza la compra si alguna butaca está bloqueada y vigente por otra persona.
     *
     * <p>Sin esta comprobación el bloqueo no serviría de nada: cualquiera podría comprar por encima
     * de él.
     */
    public void requireNotHeldByOthers(UUID screeningUuid, Collection<UUID> seatIds, UUID buyerUuid) {
        List<UUID> held = seatHoldRepository.findHeldSeatIdsAmong(
                screeningUuid, seatIds, buyerUuid, OffsetDateTime.now(clock));
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
        OffsetDateTime now = OffsetDateTime.now(clock);
        // Los vencidos sin soltar no avisaron a nadie al vencer: el aviso sale acá, con a lo sumo un
        // período de barrido de demora. Los soltados ya avisaron al soltarse.
        Map<UUID, Set<UUID>> expiredSeats = new HashMap<>();
        for (SeatHold hold : seatHoldRepository.findExpiredUnreleased(now)) {
            expiredSeats.computeIfAbsent(hold.getMovieScreening().getUuid(), _ -> new HashSet<>())
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

    private static boolean isOwnedLive(SeatHold hold, String userEmail, OffsetDateTime now) {
        return hold.getUser().getEmail().equalsIgnoreCase(userEmail) && hold.isLive(now);
    }

    private SeatHold getLiveHold(UUID uuid, String userEmail, OffsetDateTime now) {
        return seatHoldRepository.findById(uuid)
                .filter(hold -> isOwnedLive(hold, userEmail, now))
                .orElseThrow(() -> NotFoundException.of("Seat hold", uuid));
    }
}
