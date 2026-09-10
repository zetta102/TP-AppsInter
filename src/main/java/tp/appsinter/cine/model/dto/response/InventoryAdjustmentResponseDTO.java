package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.InventoryAdjustment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento del historial de stock de un producto.
 */
public record InventoryAdjustmentResponseDTO(
        UUID uuid,
        Integer delta,
        Integer resultingStock,
        String reason,
        String adjustedBy,
        OffsetDateTime createdAt
) {
    public static InventoryAdjustmentResponseDTO from(InventoryAdjustment adjustment) {
        return new InventoryAdjustmentResponseDTO(
                adjustment.getUuid(),
                adjustment.getDelta(),
                adjustment.getResultingStock(),
                adjustment.getReason(),
                adjustment.getAdjustedBy(),
                adjustment.getCreatedAt()
        );
    }
}
