package tp.appsinter.cine.model.dto.response;

import tp.appsinter.cine.model.entities.ReservationItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemResponseDTO(
        UUID uuid,
        UUID inventoryId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
    public static ReservationItemResponseDTO from(ReservationItem item) {
        return new ReservationItemResponseDTO(
                item.getUuid(),
                item.getInventory().getUuid(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
