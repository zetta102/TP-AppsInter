package tp.appsinter.cine.auth;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * El documento declara el token portador como requisito global. Las operaciones públicas lo
 * declaran opcional ({@code security: [{}, {bearerAuth: []}]}): se pueden llamar sin token, y con uno
 * válido algunas personalizan la respuesta (por ejemplo, marcan las butacas que bloqueó quien
 * consulta).
 *
 * <p>Las rutas públicas salen de {@link SecurityConfig#PUBLIC_ROUTES}, las mismas que aplica la
 * cadena de filtros.
 */
@Component
class SecurityOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null || openApi.getSecurity() == null) {
            return;
        }
        List<SecurityRequirement> optional =
                Stream.concat(Stream.of(new SecurityRequirement()), openApi.getSecurity().stream()).toList();
        for (Map.Entry<String, PathItem> path : openApi.getPaths().entrySet()) {
            for (Map.Entry<PathItem.HttpMethod, Operation> operation : path.getValue().readOperationsMap().entrySet()) {
                HttpMethod method = HttpMethod.valueOf(operation.getKey().name());
                if (isPublic(method, path.getKey())) {
                    operation.getValue().setSecurity(optional);
                }
            }
        }
    }

    private static boolean isPublic(HttpMethod method, String path) {
        return SecurityConfig.PUBLIC_ROUTES.stream().anyMatch(route -> route.matches(method, path));
    }
}
