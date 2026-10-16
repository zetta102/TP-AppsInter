package tp.appsinter.cine.config;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // Cada enumeración como esquema con nombre propio (MovieGenre, SeatType…) en lugar de
        // repetirla en línea en cada propiedad: los generadores producen un solo tipo reutilizable.
        ModelResolver.enumsAsRef = true;
    }

    @Bean
    OpenAPI cineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cine API")
                        .description("API de reservas de cine")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
