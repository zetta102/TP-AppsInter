package tp.appsinter.cine.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tp.appsinter.cine.inventory.ProductCategory;

import java.math.BigDecimal;

/**
 * Alta y edición de un producto de la confitería.
 *
 * @param cost precio de venta al público, con a lo sumo dos decimales: es lo que se guarda y lo que
 *             se cobra, así que no se redondea en silencio
 */
@Schema(name = "InventoryItemRequest")
public record InventoryItemRequestDTO(
        @NotBlank @Size(max = 255) String productName,
        @NotNull ProductCategory category,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal cost,
        @NotNull @PositiveOrZero Integer quantity
) {
}
