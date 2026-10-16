package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "InventoryItem")
public record CreateInventoryResponseDTO(
        UUID uuid,
        String productName,
        ProductCategory category,
        BigDecimal cost,
        Integer quantity,
        boolean archived
) {
    public static CreateInventoryResponseDTO from(Inventory inventory) {
        return new CreateInventoryResponseDTO(
                inventory.getUuid(),
                inventory.getProductName(),
                inventory.getCategory(),
                inventory.getCost(),
                inventory.getQuantity(),
                inventory.isArchived()
        );
    }
}
