package tp.appsinter.cine.config.openapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import tp.appsinter.cine.CineApplication;
import tp.appsinter.cine.shared.dto.PageResponseDTO;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Publica en OpenAPI la nulabilidad que el código ya declara con JSpecify.
 *
 * <p>springdoc no lee {@code @Nullable} de JSpecify, así que por sí solo marca como obligatorios
 * únicamente los campos con {@code @NotNull}/{@code @NotBlank}, y los tipos generados del lado del
 * cliente quedan todos opcionales. Este paso recorre los registros de los paquetes {@code *.dto} de
 * cada funcionalidad —que son {@code @NullMarked}— y, en su esquema:
 * <ul>
 *   <li>marca {@code required} todo componente sin {@code @Nullable};</li>
 *   <li>en una respuesta, agrega {@code "null"} al tipo de los anotados con {@code @Nullable}: siempre
 *       viajan, pero pueden valer {@code null};</li>
 *   <li>en una petición (un {@code *RequestDTO} o un registro anidado en uno), deja opcionales los
 *       anotados con {@code @Nullable}: el cliente puede omitirlos o mandarlos en {@code null};</li>
 *   <li>deja opcional y no nulo un componente con {@code @JsonInclude(NON_NULL)}, que en lugar de
 *       valer {@code null} directamente no se envía.</li>
 * </ul>
 */
@Component
public class RecordNullabilityCustomizer implements GlobalOpenApiCustomizer {

    private static final String DTO_PACKAGE_SUFFIX = ".dto";
    private static final String NULL_TYPE = "null";
    private static final String REQUEST_SUFFIX = "RequestDTO";

    private final Map<String, Class<?>> recordsBySchemaName = scanRecords();

    private static Map<String, Class<?>> scanRecords() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, Class<?>> records = new HashMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CineApplication.class.getPackageName())) {
            Class<?> type = ClassUtils.resolveClassName(Objects.requireNonNull(definition.getBeanClassName()),
                    RecordNullabilityCustomizer.class.getClassLoader());
            if (!type.getPackageName().endsWith(DTO_PACKAGE_SUFFIX)) {
                continue;
            }
            Class<?> previous = records.put(schemaName(type), type);
            if (previous != null) {
                throw new IllegalStateException("%s y %s publican el mismo esquema %s"
                        .formatted(previous.getName(), type.getName(), schemaName(type)));
            }
        }
        return Map.copyOf(records);
    }

    private static boolean isRequest(Class<?> type) {
        Class<?> outermost = type;
        while (outermost.getEnclosingClass() != null) {
            outermost = outermost.getEnclosingClass();
        }
        return outermost.getSimpleName().endsWith(REQUEST_SUFFIX);
    }

    static String schemaName(Class<?> type) {
        io.swagger.v3.oas.annotations.media.Schema schema =
                type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return schema != null && !schema.name().isEmpty() ? schema.name() : type.getSimpleName();
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        String pageName = schemaName(PageResponseDTO.class);
        openApi.getComponents().getSchemas().forEach((name, schema) -> {
            Class<?> type = recordsBySchemaName.get(name);
            if (type == null && name.startsWith(pageName)) {
                type = PageResponseDTO.class;
            }
            if (type != null && type.isRecord()) {
                apply(type, schema);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static void apply(Class<?> type, Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        boolean request = isRequest(type);
        Set<String> required = new LinkedHashSet<>(schema.getRequired() == null ? List.of() : schema.getRequired());
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            String property = jsonName(component);
            Schema<?> propertySchema = properties.get(property);
            if (propertySchema == null) {
                continue;
            }
            boolean nullable = isNullable(component);
            if (isOmittedWhenNull(component) || (request && nullable)) {
                required.remove(property);
            } else {
                required.add(property);
            }
            if (nullable && !isOmittedWhenNull(component)) {
                properties.put(property, withNull(propertySchema));
            }
        }
        schema.setRequired(required.isEmpty() ? null : new ArrayList<>(required));
    }

    private static String jsonName(RecordComponent component) {
        JsonProperty property = component.getAnnotation(JsonProperty.class);
        if (property == null) {
            property = component.getAccessor().getAnnotation(JsonProperty.class);
        }
        return property != null && !property.value().isEmpty() ? property.value() : component.getName();
    }

    private static boolean isNullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class);
    }

    private static boolean isOmittedWhenNull(RecordComponent component) {
        JsonInclude include = component.getAnnotation(JsonInclude.class);
        if (include == null) {
            include = component.getAccessor().getAnnotation(JsonInclude.class);
        }
        return include != null && include.value() != JsonInclude.Include.ALWAYS
                && include.value() != JsonInclude.Include.USE_DEFAULTS;
    }

    @SuppressWarnings("rawtypes")
    private static Schema withNull(Schema<?> schema) {
        if (schema.get$ref() != null) {
            return new Schema<>().anyOf(List.of(new Schema<>().$ref(schema.get$ref()), nullSchema()));
        }
        Set<String> types = new LinkedHashSet<>();
        if (schema.getTypes() != null) {
            types.addAll(schema.getTypes());
        } else if (schema.getType() != null) {
            types.add(schema.getType());
        }
        if (types.isEmpty()) {
            return new Schema<>().anyOf(List.of(schema, nullSchema()));
        }
        types.add(NULL_TYPE);
        schema.setTypes(types);
        return schema;
    }

    private static Schema<?> nullSchema() {
        Schema<?> schema = new Schema<>();
        schema.addType(NULL_TYPE);
        return schema;
    }
}
