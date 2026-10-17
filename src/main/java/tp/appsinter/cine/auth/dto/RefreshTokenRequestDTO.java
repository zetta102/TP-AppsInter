package tp.appsinter.cine.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshTokenRequestDTO(
        @NotBlank String refreshToken
) {
}
