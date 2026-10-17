package tp.appsinter.cine.screening;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tp.appsinter.cine.hold.SeatHoldRepository;
import tp.appsinter.cine.reservation.TicketRepository;
import tp.appsinter.cine.screening.dto.SeatStateResponseDTO;
import tp.appsinter.cine.user.UserService;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal de avisos de disponibilidad de butacas por Server-Sent Events.
 *
 * <p>Reemplaza el sondeo periódico del mapa de butacas: cada suscriptor recibe un evento
 * {@code seats} con el estado nuevo de las butacas que cambiaron, calculado para él —sus propios
 * bloqueos se ven disponibles, igual que en el mapa—. El aviso sale recién cuando la transacción que
 * hizo el cambio confirma, de modo que nunca anuncia algo que después se deshizo.
 *
 * <p>Los suscriptores viven en memoria: con más de una instancia detrás de un balanceador, cada una
 * sólo avisa los cambios que ella misma hizo. Alcanza para una instancia; para varias haría falta un
 * bus compartido.
 */
@Service
@Slf4j
public class SeatStreamService {

    static final String SEATS_EVENT = "seats";

    private final MovieScreeningService movieScreeningService;
    private final UserService userService;
    private final TicketRepository ticketRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final Duration timeout;
    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public SeatStreamService(MovieScreeningService movieScreeningService,
                             UserService userService,
                             TicketRepository ticketRepository,
                             SeatHoldRepository seatHoldRepository,
                             @Value("${cine.seat-stream.timeout:PT30M}") Duration timeout) {
        this.movieScreeningService = movieScreeningService;
        this.userService = userService;
        this.ticketRepository = ticketRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.timeout = timeout;
    }

    /**
     * Abre un canal para la función. Falla con {@code 404} antes de abrirlo si la función no existe.
     *
     * @param viewerEmail quien escucha, o {@code null} si es anónimo
     */
    public SseEmitter subscribe(UUID screeningUuid, @Nullable String viewerEmail) {
        movieScreeningService.getScreening(screeningUuid);
        UUID viewerUuid = viewerEmail == null ? null : userService.findUuidByEmail(viewerEmail);

        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        Subscriber subscriber = new Subscriber(emitter, viewerUuid);
        subscribers.compute(screeningUuid, (key, current) -> {
            Set<Subscriber> set = current != null ? current : ConcurrentHashMap.newKeySet();
            set.add(subscriber);
            return set;
        });
        Runnable remove = () -> unsubscribe(screeningUuid, subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        // Un primer comentario hace que los encabezados salgan ya y el cliente sepa que está conectado.
        send(screeningUuid, subscriber, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /**
     * Cantidad de suscriptores abiertos para la función; sirve para pruebas y diagnóstico.
     */
    public int subscriberCount(UUID screeningUuid) {
        Set<Subscriber> set = subscribers.get(screeningUuid);
        return set == null ? 0 : set.size();
    }

    /**
     * Calcula el estado nuevo de las butacas una vez por cada quien mira distinto —todos los
     * anónimos comparten el mismo— y se lo envía a cada suscriptor de la función.
     */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSeatsChanged(SeatsChangedEvent event) {
        Set<Subscriber> listening = subscribers.get(event.screeningId());
        if (listening == null || listening.isEmpty()) {
            return;
        }
        UUID screeningUuid = event.screeningId();
        List<UUID> seatIds = List.copyOf(event.seatIds());
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> booked = new HashSet<>(ticketRepository.findBookedSeatIdsAmong(screeningUuid, seatIds));

        Map<Optional<UUID>, List<SeatStateResponseDTO>> byViewer = new HashMap<>();
        for (Subscriber subscriber : listening) {
            List<SeatStateResponseDTO> states = byViewer.computeIfAbsent(
                    Optional.ofNullable(subscriber.viewerUuid()),
                    viewer -> statesFor(screeningUuid, seatIds, booked, viewer.orElse(null), now));
            send(screeningUuid, subscriber,
                    SseEmitter.event().name(SEATS_EVENT).data(states, MediaType.APPLICATION_JSON));
        }
    }

    private List<SeatStateResponseDTO> statesFor(UUID screeningUuid,
                                                 List<UUID> seatIds,
                                                 Set<UUID> booked,
                                                 @Nullable UUID viewerUuid,
                                                 OffsetDateTime now) {
        Set<UUID> heldByOthers = new HashSet<>(
                seatHoldRepository.findHeldSeatIdsAmong(screeningUuid, seatIds, viewerUuid, now));
        return seatIds.stream()
                .map(seatId -> {
                    boolean sold = booked.contains(seatId);
                    boolean held = !sold && heldByOthers.contains(seatId);
                    return new SeatStateResponseDTO(seatId, !sold && !held, held);
                })
                .toList();
    }

    /**
     * Comentario periódico para que proxies y balanceadores no corten una conexión inactiva, y para
     * descubrir y soltar las que el cliente ya cerró.
     */
    @Scheduled(fixedDelayString = "${cine.seat-stream.heartbeat:PT25S}")
    public void heartbeat() {
        subscribers.forEach((screeningUuid, set) ->
                set.forEach(subscriber -> send(screeningUuid, subscriber, SseEmitter.event().comment("keep-alive"))));
    }

    @PreDestroy
    void closeAll() {
        subscribers.values().forEach(set -> set.forEach(subscriber -> subscriber.emitter().complete()));
        subscribers.clear();
    }

    private void send(UUID screeningUuid, Subscriber subscriber, SseEmitter.SseEventBuilder event) {
        try {
            subscriber.emitter().send(event);
        } catch (IOException | IllegalStateException closed) {
            log.debug("Dropping closed seat stream for screening {}", screeningUuid, closed);
            unsubscribe(screeningUuid, subscriber);
        }
    }

    private void unsubscribe(UUID screeningUuid, Subscriber subscriber) {
        subscribers.computeIfPresent(screeningUuid, (key, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Un canal abierto. La igualdad es la del emisor, que no redefine {@code equals}.
     */
    private record Subscriber(SseEmitter emitter, @Nullable UUID viewerUuid) {
    }
}
