package tp.appsinter.cine.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Conecta el appender {@code OTEL} de {@code logback-spring.xml} con el SDK de OpenTelemetry.
 *
 * <p>Logback arranca antes que el contexto de Spring, así que el appender existe desde el principio
 * pero no tiene a dónde exportar hasta que este bean le entrega el SDK ya configurado.
 */
@Component
@RequiredArgsConstructor
class OpenTelemetryAppenderInstaller implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
