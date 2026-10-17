package tp.appsinter.cine.screening.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alta y edición de una función.
 *
 * @param startsAt debe ser futuro al crear y al cambiar el horario. No lleva {@code @Future}: al
 *                 editar sólo el precio de una función ya empezada se reenvía el horario original,
 *                 que ya pasó. La regla la aplica {@code MovieScreeningService}, con la misma
 *                 respuesta {@code 400 VALIDATION_FAILED} que daría la anotación.
 */
@Schema(name = "ScreeningRequest")
public record ScreeningRequestDTO(
        @NotNull UUID movieId,
        @NotNull UUID screenRoomId,
        @NotNull OffsetDateTime startsAt,
        @NotNull @DecimalMin("0.0") BigDecimal basePrice
) {
}
