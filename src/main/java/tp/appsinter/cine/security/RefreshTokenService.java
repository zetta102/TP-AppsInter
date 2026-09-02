package tp.appsinter.cine.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.model.entities.RefreshToken;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues and rotates opaque refresh tokens. The raw value is returned to the caller exactly once;
 * only its SHA-256 hash is stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plus(properties.refreshExpiration()))
                .build();
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(token, entity.getExpiresAt());
    }

    /**
     * Consumes a refresh token and issues a replacement. Rotation means a stolen token stops working
     * as soon as the legitimate client refreshes.
     */
    @Transactional
    public RotatedRefreshToken rotate(String token) {
        RefreshToken existing = requireActive(token);
        existing.setRevokedAt(OffsetDateTime.now());
        User user = existing.getUser();
        return new RotatedRefreshToken(user, issue(user));
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByTokenHash(hash(token))
                .filter(refreshToken -> refreshToken.getRevokedAt() == null)
                .ifPresent(refreshToken -> refreshToken.setRevokedAt(OffsetDateTime.now()));
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllForUser(user.getUuid(), OffsetDateTime.now());
    }

    private RefreshToken requireActive(String token) {
        return refreshTokenRepository.findByTokenHash(hash(token))
                .filter(refreshToken -> refreshToken.isActive(OffsetDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid or expired"));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record IssuedRefreshToken(String token, OffsetDateTime expiresAt) {
    }

    public record RotatedRefreshToken(User user, IssuedRefreshToken refreshToken) {
    }
}
