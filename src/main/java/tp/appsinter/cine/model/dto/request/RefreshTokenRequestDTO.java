package tp.appsinter.cine.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank String refreshToken
) {
}
