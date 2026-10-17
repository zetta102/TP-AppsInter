package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.model.entities.InventoryAdjustment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento del historial de stock de un producto.
 */
@Schema(name = "InventoryAdjustment")
public record InventoryAdjustmentResponseDTO(
        UUID uuid,
        Integer delta,
        Integer resultingStock,
        String reason,
        @Nullable String adjustedBy,
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
