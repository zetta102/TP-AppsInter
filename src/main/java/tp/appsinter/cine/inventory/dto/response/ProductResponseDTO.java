package tp.appsinter.cine.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proyección pública del catálogo de confitería.
 *
 * <p>Es deliberadamente más pobre que {@link InventoryItemResponseDTO}: el stock es un dato
 * operativo y no se publica. En su lugar se expone {@code available}, que dice si queda mercadería
 * sin revelar cuánta. {@code price} es el precio de venta, el mismo valor que el DTO de
 * administración llama {@code cost}.
 */
@Schema(name = "Product")
public record ProductResponseDTO(
        UUID uuid,
        String productName,
        ProductCategory category,
        BigDecimal price,
        boolean available
) {
    public static ProductResponseDTO from(Inventory inventory) {
        return new ProductResponseDTO(
                inventory.getUuid(),
                inventory.getProductName(),
                inventory.getCategory(),
                inventory.getCost(),
                !inventory.isArchived() && inventory.getQuantity() != null && inventory.getQuantity() > 0
        );
    }
}
