package tp.appsinter.cine.config;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj único de la aplicación.
 *
 * <p>Toda decisión que depende de la hora —si una función ya empezó, si un bloqueo venció, si un
 * token sigue dentro de la ventana de gracia— lo pide inyectado en lugar de leer la hora del sistema.
 * Así las pruebas pueden fijarlo o adelantarlo sin esperar, y todos los instantes salen en UTC, el
 * mismo desplazamiento con el que la base devuelve las columnas {@code TIMESTAMPTZ}.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Las restricciones que dependen de la fecha ({@code @Future}, {@code @MaxYearsAhead}…) usan el
     * mismo reloj que el resto de la aplicación.
     */
    @Bean
    ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
