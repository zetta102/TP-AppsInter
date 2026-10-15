package tp.appsinter.cine;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deja la base vacía entre pruebas.
 *
 * <p>Reemplaza las cadenas de {@code deleteAll()} que cada clase de prueba ordenaba a mano según
 * las claves foráneas: con {@code CASCADE} alcanza con nombrar las tablas raíz, y una tabla nueva
 * que cuelgue de ellas queda cubierta sin tocar ninguna prueba. El historial de Flyway no se toca.
 */
public final class DatabaseCleaner {

    private static final String TRUNCATE_ALL = "TRUNCATE app_user, movie, screen_room, inventory CASCADE";

    private DatabaseCleaner() {
    }

    public static void truncate(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(TRUNCATE_ALL);
    }
}
