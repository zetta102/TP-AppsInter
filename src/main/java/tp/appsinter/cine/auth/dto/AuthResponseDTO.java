package tp.appsinter.cine.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "AuthResponse")
public record AuthResponseDTO(
        String token,
        String tokenType,
        OffsetDateTime expiresAt,
        String refreshToken,
        OffsetDateTime refreshExpiresAt
) {
    public static AuthResponseDTO bearer(String token,
                                         OffsetDateTime expiresAt,
                                         String refreshToken,
                                         OffsetDateTime refreshExpiresAt) {
        return new AuthResponseDTO(token, "Bearer", expiresAt, refreshToken, refreshExpiresAt);
    }
}
