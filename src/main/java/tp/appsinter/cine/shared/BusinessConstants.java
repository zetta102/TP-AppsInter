package tp.appsinter.cine.shared;

import java.math.BigDecimal;

/**
 * Constantes de negocio que también se publican en {@code GET /api/metadata/config}.
 *
 * <p>Están acá y no repartidas por los servicios para que el número que el cliente muestra y el que
 * el servidor cobra sean literalmente el mismo. Antes, el 1,5 del recargo premium y los 15 minutos
 * de limpieza entre funciones vivían duplicados en el frontend, y nada avisaba cuando dejaban de
 * coincidir.
 */
public final class BusinessConstants {

    /**
     * Recargo de una butaca premium sobre el precio base de la función.
     */
    public static final BigDecimal PREMIUM_SEAT_MULTIPLIER = new BigDecimal("1.50");

    /**
     * Minutos que deben quedar libres entre dos funciones de la misma sala, para limpieza y entrada
     * del público.
     */
    public static final int SCREENING_TURNOVER_MINUTES = 15;

    /**
     * Moneda en la que están expresados todos los importes. El sistema no maneja más de una.
     */
    public static final String CURRENCY = "USD";

    private BusinessConstants() {
    }
}
