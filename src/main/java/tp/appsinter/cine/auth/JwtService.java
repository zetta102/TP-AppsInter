package tp.appsinter.cine.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite y valida los tokens de acceso: JWT firmados con HMAC-SHA256.
 *
 * <p>El token sólo lleva quién es ({@code sub}, el correo), quién lo emitió y cuándo vence. El rol
 * no viaja en él: el filtro de autenticación lo vuelve a leer de la base en cada petición, así que un
 * cambio de rol o una baja rigen de inmediato y no recién cuando el token vence.
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    /**
     * La clave se deriva una sola vez: el secreto no cambia en caliente y el filtro de autenticación
     * la usa en cada petición.
     */
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /**
     * El algoritmo se fija en HS256 en lugar de dejar que jjwt lo elija por el largo de la clave: con
     * un secreto más largo pasaría a HS384 o HS512 sin que nadie lo decidiera.
     */
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.expiration());
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt.atOffset(ZoneOffset.UTC));
    }

    /**
     * Analiza un token de acceso distinguiendo el vencimiento del resto de los fallos, para que la
     * respuesta {@code 401} pueda llevar {@code TOKEN_EXPIRED} y el cliente sepa que le alcanza con
     * renovar en lugar de volver a iniciar sesión.
     */
    public ParsedAccessToken parse(String token) {
        try {
            return new ParsedAccessToken(parser.parseSignedClaims(token).getPayload(), false);
        } catch (ExpiredJwtException _) {
            return new ParsedAccessToken(null, true);
        } catch (JwtException | IllegalArgumentException _) {
            return new ParsedAccessToken(null, false);
        }
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    /**
     * @param claims  reclamaciones del token, o {@code null} si no se pudo validar
     * @param expired {@code true} sólo cuando la firma era correcta pero el token venció
     */
    public record ParsedAccessToken(@Nullable Claims claims, boolean expired) {

        public Optional<Claims> claimsIfValid() {
            return Optional.ofNullable(claims);
        }
    }
}
