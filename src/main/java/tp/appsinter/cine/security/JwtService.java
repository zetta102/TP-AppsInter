package tp.appsinter.cine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.model.entities.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final JwtProperties properties;
    /**
     * La clave se deriva una sola vez: el secreto no cambia en caliente y el filtro de autenticación
     * la usa en cada petición.
     */
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiration());
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("uid", user.getUuid().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
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
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedAccessToken(claims, false);
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
