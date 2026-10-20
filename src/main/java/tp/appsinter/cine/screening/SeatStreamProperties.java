package tp.appsinter.cine.screening;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Ajustes del canal de avisos de butacas.
 *
 * @param timeout   vida máxima de una conexión; al vencer, el cliente se reconecta
 * @param heartbeat cada cuánto se envía un comentario para que proxies y balanceadores no corten una
 *                  conexión inactiva, en formato ISO-8601 porque {@code @Scheduled} lo lee como texto
 */
@Validated
@ConfigurationProperties(prefix = "cine.seat-stream")
public record SeatStreamProperties(
        @NotNull Duration timeout,
        @NotNull Duration heartbeat
) {
}
