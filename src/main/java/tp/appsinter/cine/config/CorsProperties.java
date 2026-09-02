package tp.appsinter.cine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Cross-origin settings for browser clients. Defaults cover the usual local dev servers
 * (Create React App / Next.js on 3000, Vite on 5173).
 */
@ConfigurationProperties(prefix = "cine.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://localhost:5173"}) List<String> allowedOrigins,
        @DefaultValue({"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"}) List<String> allowedMethods,
        @DefaultValue("*") List<String> allowedHeaders,
        @DefaultValue({"Location"}) List<String> exposedHeaders,
        @DefaultValue("false") boolean allowCredentials,
        @DefaultValue("1h") Duration maxAge
) {
}
