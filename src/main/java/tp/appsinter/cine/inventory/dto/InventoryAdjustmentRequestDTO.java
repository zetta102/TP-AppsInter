package tp.appsinter.cine.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ajuste de stock de un producto.
 *
 * <p>Se pide la variación y no la cantidad final: dos personas reponiendo a la vez se suman en lugar
 * de pisarse, que es exactamente lo que no lograba el reemplazo completo del producto.
 *
 * @param delta  variación a aplicar; positiva al recibir mercadería, negativa al dar de baja. Cero
 *               no describe ningún movimiento y se rechaza.
 * @param reason motivo del ajuste, obligatorio: un historial sin motivos no explica nada
 */
@Schema(name = "InventoryAdjustmentRequest")
public record InventoryAdjustmentRequestDTO(
        @NotNull Integer delta,
        @NotBlank @Size(max = 255) String reason
) {
}
