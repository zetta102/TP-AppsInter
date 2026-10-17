package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proyección pública del catálogo de confitería.
 *
 * <p>Es deliberadamente más pobre que {@link CreateInventoryResponseDTO}: el stock y el costo de
 * reposición son datos operativos y no se publican. En su lugar se expone {@code available}, que
 * dice si queda mercadería sin revelar cuánta, y {@code price}, que es el nombre orientado al
 * cliente de lo que el DTO de administración llama {@code cost}.
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
