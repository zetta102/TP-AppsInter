package tp.appsinter.cine.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.math.BigDecimal;

@Schema(name = "InventoryRequest")
public record CreateInventoryRequestDTO(
        @NotBlank String productName,
        @NotNull ProductCategory category,
        @NotNull @DecimalMin("0.0") BigDecimal cost,
        @NotNull @PositiveOrZero Integer quantity
) {
}
