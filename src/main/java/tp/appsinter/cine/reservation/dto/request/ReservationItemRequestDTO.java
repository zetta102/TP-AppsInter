package tp.appsinter.cine.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(name = "ReservationItemRequest")
public record ReservationItemRequestDTO(
        @NotNull UUID inventoryId,
        @NotNull @Positive Integer quantity
) {
}
