package tp.appsinter.cine.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tp.appsinter.cine.reservation.ReservationItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param cancelledAt momento en que la línea se canceló junto con su reserva, o {@code null} si sigue
 *                    vigente
 */
@Schema(name = "ReservationItem")
public record ReservationItemResponseDTO(
        UUID uuid,
        UUID inventoryId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        @Nullable OffsetDateTime cancelledAt
) {
    public static ReservationItemResponseDTO from(ReservationItem item) {
        return new ReservationItemResponseDTO(
                item.getUuid(),
                item.getInventory().getUuid(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                item.getCancelledAt()
        );
    }
}
