package tp.appsinter.cine.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de integración contra la aplicación completa y PostgreSQL real.
 *
 * <p>Todas las clases que la usan comparten un único contexto de Spring —misma configuración, mismo
 * contexto en caché—, y cada prueba arranca con la base vacía y el reloj en la hora real
 * ({@link DatabaseCleanerExtension}). Trae {@code MockMvcTester}, {@link ApiClient},
 * {@link CatalogueFixture} y {@link MutableClock}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, ApiTestConfiguration.class})
@ExtendWith(DatabaseCleanerExtension.class)
public @interface ApiIntegrationTest {
}
