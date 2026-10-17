package tp.appsinter.cine.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Constantes de negocio que los clientes necesitan para mostrar precios y armar horarios.
 *
 * <p>Ninguna es secreta y todas estaban duplicadas en el frontend. Publicarlas evita que el importe
 * que el usuario ve antes de confirmar difiera del que el servidor cobra.
 *
 * @param premiumSeatMultiplier    factor que se aplica al precio base en las butacas premium
 * @param screeningTurnoverMinutes minutos libres exigidos entre dos funciones de la misma sala
 * @param currency                 moneda de todos los importes de la API
 * @param seatHoldTtlSeconds       cuánto dura un bloqueo de butacas antes de vencer
 */
@Schema(name = "MetadataConfig")
public record MetadataConfigResponseDTO(
        BigDecimal premiumSeatMultiplier,
        int screeningTurnoverMinutes,
        String currency,
        long seatHoldTtlSeconds
) {
}
