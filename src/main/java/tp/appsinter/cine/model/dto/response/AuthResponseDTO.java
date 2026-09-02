package tp.appsinter.cine.model.dto.response;

import java.time.OffsetDateTime;

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
