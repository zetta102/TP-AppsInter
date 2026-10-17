package tp.appsinter.cine.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "AuthTokens")
public record AuthTokensResponseDTO(
        String token,
        String tokenType,
        OffsetDateTime expiresAt,
        String refreshToken,
        OffsetDateTime refreshExpiresAt
) {
    public static AuthTokensResponseDTO bearer(String token,
                                         OffsetDateTime expiresAt,
                                         String refreshToken,
                                         OffsetDateTime refreshExpiresAt) {
        return new AuthTokensResponseDTO(token, "Bearer", expiresAt, refreshToken, refreshExpiresAt);
    }
}
