package tp.appsinter.cine.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de los tokens.
 *
 * @param secret            clave de firma del token de acceso
 * @param expiration        vigencia del token de acceso
 * @param refreshExpiration vigencia del token de refresco
 * @param refreshGrace      ventana durante la cual un token ya rotado se sigue aceptando. Sin ella,
 *                          dos pestañas refrescando a la vez o una respuesta perdida cierran la
 *                          sesión sin que el usuario haya hecho nada raro. Pasada la ventana, volver
 *                          a presentarlo se trata como reutilización y se revoca la familia entera.
 * @param refreshSweepDelay cada cuánto se borran los tokens vencidos, en formato ISO-8601 porque
 *                          {@code @Scheduled} lo lee como texto
 * @param issuer            emisor declarado en el token
 */
@Validated
@ConfigurationProperties(prefix = "cine.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration expiration,
        @NotNull Duration refreshExpiration,
        @NotNull Duration refreshGrace,
        @NotNull Duration refreshSweepDelay,
        @NotBlank String issuer
) {
}
