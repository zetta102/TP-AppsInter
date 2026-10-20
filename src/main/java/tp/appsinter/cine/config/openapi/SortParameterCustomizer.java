package tp.appsinter.cine.config.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import tp.appsinter.cine.shared.web.AllowedSort;
import tp.appsinter.cine.shared.web.SortWhitelist;

import java.util.List;

/**
 * Publica en el parámetro {@code sort} de cada listado las propiedades que declara su
 * {@link AllowedSort}, como extensión {@code x-sort-properties} y en la descripción. La lista sale
 * de la misma {@link SortWhitelist} que valida la petición, así que la documentación no puede
 * desfasarse.
 */
@Component
class SortParameterCustomizer implements GlobalOperationCustomizer {

    private static final String EXTENSION = "x-sort-properties";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getParameters() == null) {
            return operation;
        }
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            SortWhitelist.forParameter(parameter).ifPresent(whitelist -> operation.getParameters().stream()
                    .filter(candidate -> "sort".equals(candidate.getName()) && "query".equals(candidate.getIn()))
                    .forEach(sort -> document(sort, whitelist.publishedProperties())));
        }
        return operation;
    }

    private static void document(Parameter sort, List<String> properties) {
        sort.addExtension(EXTENSION, properties);
        sort.setDescription("Sorting criteria in the format property(,asc|desc); repeatable. "
                + "Allowed properties: " + String.join(", ", properties)
                + ". Any other property is rejected with 400 INVALID_SORT_PROPERTY.");
    }
}
