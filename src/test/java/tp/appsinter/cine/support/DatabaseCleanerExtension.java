package tp.appsinter.cine.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Deja cada prueba sobre una base vacía, el reloj en la hora real y sin sesiones de pruebas
 * anteriores.
 *
 * <p>Con {@code CASCADE} alcanza con nombrar las tablas raíz: una tabla nueva que cuelgue de ellas
 * queda cubierta sin tocar ninguna prueba. El historial de Flyway no se toca. Corre antes de los
 * {@code @BeforeEach} de la clase, así que éstos ya arman sus datos sobre la base limpia.
 */
public final class DatabaseCleanerExtension implements BeforeEachCallback {

    private static final String TRUNCATE_ALL = "TRUNCATE app_user, movie, screen_room, inventory CASCADE";

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext spring = SpringExtension.getApplicationContext(context);
        spring.getBean(JdbcTemplate.class).execute(TRUNCATE_ALL);
        spring.getBeanProvider(MutableClock.class).ifAvailable(MutableClock::reset);
        spring.getBeanProvider(ApiClient.class).ifAvailable(ApiClient::forgetTokens);
    }
}
