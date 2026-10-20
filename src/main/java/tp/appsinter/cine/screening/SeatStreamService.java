package tp.appsinter.cine.screening;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tp.appsinter.cine.screening.dto.response.SeatStateResponseDTO;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
@RequiredArgsConstructor
@Slf4j
public class SeatStreamService {

    static final String SEATS_EVENT = "seats";

    private final MovieScreeningService movieScreeningService;
    private final SoldSeats soldSeats;
    private final HeldSeats heldSeats;
    private final SeatStreamProperties properties;
    private final Clock clock;
    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /**
     * Abre un canal para la función. Falla con {@code 404} antes de abrirlo si la función no existe.
     *
     * @param viewerEmail quien escucha, o {@code null} si es anónimo
     */
    public SseEmitter subscribe(UUID screeningUuid, @Nullable String viewerEmail) {
        movieScreeningService.getScreening(screeningUuid);
        UUID viewerUuid = movieScreeningService.viewerUuid(viewerEmail);

        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        Subscriber subscriber = new Subscriber(emitter, new Viewer(viewerUuid));
        subscribers.computeIfAbsent(screeningUuid, _ -> ConcurrentHashMap.newKeySet()).add(subscriber);
        Runnable remove = () -> unsubscribe(screeningUuid, subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(_ -> remove.run());
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
     *
     * <p>Se atiende después de que la transacción que publicó el evento confirma; si el evento se
     * publicó fuera de una transacción, se atiende igual ({@code fallbackExecution}).
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
        OffsetDateTime now = OffsetDateTime.now(clock);
        Set<UUID> booked = new HashSet<>(soldSeats.findIdsAmong(screeningUuid, seatIds));

        Map<Viewer, List<SeatStateResponseDTO>> byViewer = new HashMap<>();
        for (Subscriber subscriber : listening) {
            List<SeatStateResponseDTO> states = byViewer.computeIfAbsent(
                    subscriber.viewer(),
                    viewer -> statesFor(screeningUuid, seatIds, booked, viewer.uuid(), now));
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
                heldSeats.findIdsAmong(screeningUuid, seatIds, viewerUuid, now));
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
    @Scheduled(fixedDelayString = "${cine.seat-stream.heartbeat}")
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
        subscribers.computeIfPresent(screeningUuid, (_, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Quien mira, como clave de agrupación: todos los anónimos son el mismo espectador.
     */
    private record Viewer(@Nullable UUID uuid) {
    }

    /**
     * Un canal abierto. La igualdad es la del emisor, que no redefine {@code equals}.
     */
    private record Subscriber(SseEmitter emitter, Viewer viewer) {
    }
}
