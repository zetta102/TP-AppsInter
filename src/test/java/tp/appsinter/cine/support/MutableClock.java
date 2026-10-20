package tp.appsinter.cine.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj de pruebas: avanza con el del sistema, pero una prueba puede adelantarlo para simular que
 * pasó el tiempo —un token fuera de su ventana de gracia, un bloqueo vencido— sin esperar ni tocar
 * la base a mano. {@link DatabaseCleanerExtension} lo vuelve a la hora real antes de cada prueba.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public void advance(Duration amount) {
        offset.accumulateAndGet(amount, Duration::plus);
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The application clock is always UTC");
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset.get());
    }
}
