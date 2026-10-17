package tp.appsinter.cine.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.user.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emite y rota los tokens de refresco opacos. El valor en claro se devuelve al cliente una única
 * vez; en la base sólo queda su hash SHA-256.
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
        return issue(user, null);
    }

    /**
     * Emite un token dentro de una familia. Con {@code familyId} nulo se abre una familia nueva, que
     * es lo que corresponde al iniciar sesión.
     */
    @Transactional
    public IssuedRefreshToken issue(User user, @Nullable UUID familyId) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plus(properties.refreshExpiration()))
                .familyId(familyId != null ? familyId : UUID.randomUUID())
                .build();
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(token, entity.getExpiresAt());
    }

    /**
     * Consume un token de refresco y emite su reemplazo.
     *
     * <p>Rotar hace que un token robado deje de servir en cuanto el cliente legítimo refresque. Pero
     * rotar sin tolerancia también castiga situaciones normales — dos pestañas, un doble clic, una
     * respuesta que se pierde en el camino —, así que un token ya rotado se sigue aceptando durante
     * {@code cine.jwt.refresh-grace}: dentro de esa ventana se emite otro token de la misma familia
     * y no se revoca nada.
     *
     * <p>Presentarlo después de la ventana sí es reutilización: se revoca la familia completa. No se
     * puede saber si quien insiste es el dueño o el ladrón, y dejar afuera a los dos es más seguro
     * que dejar entrar a uno de ellos.
     *
     * <p>La revocación tiene que sobrevivir a la excepción que avisa del robo; sin
     * {@code noRollbackFor} se desharía junto con la transacción y la familia seguiría viva.
     */
    @Transactional(noRollbackFor = RefreshTokenReusedException.class)
    public RotatedRefreshToken rotate(String token) {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid or expired"));

        if (existing.isWithinGrace(now, properties.refreshGrace())) {
            log.debug("Refresh token replayed within the grace window for family {}", existing.getFamilyId());
            User user = existing.getUser();
            return new RotatedRefreshToken(user, issue(user, existing.getFamilyId()));
        }
        if (!existing.isActive(now)) {
            if (existing.getRotatedAt() != null || existing.getRevokedAt() != null) {
                refreshTokenRepository.revokeFamily(existing.getFamilyId(), now);
                log.warn("Refresh token reuse detected; revoked family {}", existing.getFamilyId());
                throw new RefreshTokenReusedException("Refresh token has already been used");
            }
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        existing.setRotatedAt(now);
        User user = existing.getUser();
        return new RotatedRefreshToken(user, issue(user, existing.getFamilyId()));
    }

    /**
     * Cierre de sesión. Revoca la familia completa y no sólo el token presentado: dentro de la
     * ventana de gracia un token ya rotado todavía puede emitir otro, así que revocar uno solo
     * dejaría la sesión con vida.
     */
    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByTokenHash(hash(token))
                .ifPresent(refreshToken ->
                        refreshTokenRepository.revokeFamily(refreshToken.getFamilyId(), OffsetDateTime.now()));
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllForUser(user.getUuid(), OffsetDateTime.now());
    }

    /**
     * Revoca todas las sesiones del usuario salvo la que presenta {@code keep}.
     *
     * <p>Se usa al cambiar la contraseña: la idea es echar a quien haya entrado con la contraseña
     * vieja sin echar de paso a quien acaba de cambiarla. Si {@code keep} es nulo o desconocido, se
     * revocan todas.
     */
    @Transactional
    public void revokeAllExcept(User user, @Nullable String keep) {
        OffsetDateTime now = OffsetDateTime.now();
        if (keep == null || keep.isBlank()) {
            refreshTokenRepository.revokeAllForUser(user.getUuid(), now);
            return;
        }
        refreshTokenRepository.revokeAllForUserExcept(user.getUuid(), hash(keep), now);
    }

    /**
     * Borra los tokens vencidos.
     *
     * <p>Es sólo higiene de la tabla: un token vencido ya no sirve aunque siga guardado. Los rotados
     * se van con el mismo barrido cuando les llega el vencimiento, que es siempre posterior.
     */
    @Scheduled(fixedDelayString = "${cine.jwt.refresh-sweep-delay}")
    @Transactional
    public void sweepExpired() {
        int deleted = refreshTokenRepository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.debug("Deleted {} expired refresh tokens", deleted);
        }
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
