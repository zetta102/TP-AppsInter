package tp.appsinter.cine.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.reservation.ReservationItem;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "ReservationItem")
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
