package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateInventoryResponseDTO(
        UUID uuid,
        String productName,
        ProductCategory category,
        BigDecimal cost,
        Integer quantity
) {
    public static CreateInventoryResponseDTO from(Inventory inventory) {
        return new CreateInventoryResponseDTO(
                inventory.getUuid(),
                inventory.getProductName(),
                inventory.getCategory(),
                inventory.getCost(),
                inventory.getQuantity()
        );
    }
}
