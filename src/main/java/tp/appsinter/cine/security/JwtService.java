package tp.appsinter.cine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;

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
                .signWith(signingKey())
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
                    .verifyWith(signingKey())
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedAccessToken(claims, false);
        } catch (ExpiredJwtException ex) {
            return new ParsedAccessToken(null, true);
        } catch (JwtException | IllegalArgumentException ex) {
            return new ParsedAccessToken(null, false);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    /**
     * @param claims  reclamaciones del token, o {@code null} si no se pudo validar
     * @param expired {@code true} sólo cuando la firma era correcta pero el token venció
     */
    public record ParsedAccessToken(Claims claims, boolean expired) {

        public Optional<Claims> claimsIfValid() {
            return Optional.ofNullable(claims);
        }
    }
}
