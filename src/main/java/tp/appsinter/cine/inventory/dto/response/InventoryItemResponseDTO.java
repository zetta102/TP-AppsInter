package tp.appsinter.cine.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "InventoryItem")
public record InventoryItemResponseDTO(
        UUID uuid,
        String productName,
        ProductCategory category,
        BigDecimal cost,
        Integer quantity,
        boolean archived
) {
    public static InventoryItemResponseDTO from(Inventory inventory) {
        return new InventoryItemResponseDTO(
                inventory.getUuid(),
                inventory.getProductName(),
                inventory.getCategory(),
                inventory.getCost(),
                inventory.getQuantity(),
                inventory.isArchived()
        );
    }
}
