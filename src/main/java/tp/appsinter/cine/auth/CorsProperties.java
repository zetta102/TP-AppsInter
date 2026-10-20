package tp.appsinter.cine.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuración de origen cruzado para clientes de navegador.
 *
 * @param allowedOrigins  orígenes del frontend. El valor por defecto cubre los servidores de
 *                        desarrollo habituales (Create React App / Next.js en 3000, Vite en 5173);
 *                        en producción se agrega el dominio desplegado
 * @param allowedMethods  incluye {@code HEAD}, porque las lecturas públicas lo admiten para que el
 *                        cliente valide su caché sin descargar el cuerpo
 * @param exposedHeaders  encabezados que el navegador deja leer al cliente; {@code Location} es el
 *                        que dice dónde quedó un recurso recién creado
 */
@Validated
@ConfigurationProperties(prefix = "cine.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://localhost:5173"}) @NotEmpty List<String> allowedOrigins,
        @DefaultValue({"GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"}) @NotEmpty List<String> allowedMethods,
        @DefaultValue("*") @NotEmpty List<String> allowedHeaders,
        @DefaultValue({"Location"}) List<String> exposedHeaders,
        @DefaultValue("false") boolean allowCredentials,
        @DefaultValue("1h") @NotNull Duration maxAge
) {
}
