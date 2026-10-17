package tp.appsinter.cine.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "InventoryItem")
public record InventoryResponseDTO(
        UUID uuid,
        String productName,
        ProductCategory category,
        BigDecimal cost,
        Integer quantity,
        boolean archived
) {
    public static InventoryResponseDTO from(Inventory inventory) {
        return new InventoryResponseDTO(
                inventory.getUuid(),
                inventory.getProductName(),
                inventory.getCategory(),
                inventory.getCost(),
                inventory.getQuantity(),
                inventory.isArchived()
        );
    }
}
