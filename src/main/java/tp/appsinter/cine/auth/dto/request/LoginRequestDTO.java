package tp.appsinter.cine.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest")
public record LoginRequestDTO(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
