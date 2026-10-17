package tp.appsinter.cine.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Ajustes de la reserva temporal de butacas.
 *
 * @param ttl        cuánto vive un bloqueo desde que se crea. Cinco minutos alcanzan para elegir
 *                   confitería y pagar, y son poco como para que una compra abandonada mantenga
 *                   butacas fuera de venta en una noche de estreno.
 * @param sweepDelay cada cuánto se borran los bloqueos ya vencidos, en formato ISO-8601 porque
 *                   {@code @Scheduled} lo lee como texto. Es sólo limpieza: el vencimiento se
 *                   evalúa al leer, así que atrasarse no bloquea ninguna venta.
 */
@Validated
@ConfigurationProperties(prefix = "cine.seat-hold")
public record SeatHoldProperties(
        @NotNull Duration ttl,
        @NotNull Duration sweepDelay
) {
}
