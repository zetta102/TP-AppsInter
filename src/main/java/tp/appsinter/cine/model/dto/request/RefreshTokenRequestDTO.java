package tp.appsinter.cine.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshTokenRequestDTO(
        @NotBlank String refreshToken
) {
}
