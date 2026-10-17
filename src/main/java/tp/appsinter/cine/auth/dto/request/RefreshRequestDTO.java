package tp.appsinter.cine.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshRequestDTO(
        @NotBlank String refreshToken
) {
}
