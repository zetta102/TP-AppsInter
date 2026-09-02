package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReservationItemRequestDTO(
        @NotNull UUID inventoryId,
        @NotNull @Positive Integer quantity
) {
}
