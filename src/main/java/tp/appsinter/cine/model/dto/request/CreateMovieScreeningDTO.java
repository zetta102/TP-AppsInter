package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateMovieScreeningDTO(
        @NotNull UUID movieId,
        @NotNull UUID screenRoomId,
        @NotNull @Future OffsetDateTime startsAt,
        @NotNull @DecimalMin("0.0") BigDecimal basePrice
) {
}
