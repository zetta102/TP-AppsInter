package tp.appsinter.cine.shared.web;

import lombok.Getter;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Propiedades por las que un listado admite ordenarse, con su traducción al modelo.
 *
 * <p>Sin esta lista, {@code sort} aceptaba cualquier camino de la entidad: ordenar usuarios por
 * {@code password} revelaba el orden de los hashes, y los nombres internos del modelo pasaban a ser
 * parte del contrato. Cada controlador declara qué publica y con qué nombre ({@link AllowedSort});
 * cualquier otra propiedad responde {@code 400 INVALID_SORT_PROPERTY}.
 *
 * @param properties nombre publicado → atributo real de la entidad, en el orden declarado
 */
public record SortWhitelist(Map<String, String> properties) {

    public SortWhitelist {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /**
     * La lista que declara un parámetro {@code Pageable} con {@link AllowedSort}, si la declara. Es
     * la única lectura de la anotación: la usan tanto la validación como la documentación.
     */
    public static Optional<SortWhitelist> forParameter(MethodParameter parameter) {
        return Optional.ofNullable(parameter.getParameterAnnotation(AllowedSort.class))
                .map(SortWhitelist::of);
    }

    static SortWhitelist of(AllowedSort allowed) {
        SortWhitelist whitelist = of(allowed.value());
        for (AllowedSort.Alias alias : allowed.aliases()) {
            whitelist = whitelist.alias(alias.name(), alias.property());
        }
        return whitelist;
    }

    /**
     * Lista en la que el nombre publicado coincide con el de la entidad.
     */
    static SortWhitelist of(String... properties) {
        Map<String, String> identity = new LinkedHashMap<>();
        for (String property : properties) {
            identity.put(property, property);
        }
        return new SortWhitelist(identity);
    }

    /**
     * Agrega un nombre publicado que se traduce a otro atributo de la entidad.
     */
    SortWhitelist alias(String published, String entityProperty) {
        Map<String, String> extended = new LinkedHashMap<>(properties);
        extended.put(published, entityProperty);
        return new SortWhitelist(extended);
    }

    /**
     * Nombres publicados, en el orden declarado.
     */
    public List<String> publishedProperties() {
        return List.copyOf(properties.keySet());
    }

    /**
     * Valida y traduce el orden pedido. Sin orden, devuelve la página tal cual.
     *
     * <p>La página traducida recuerda el orden tal como se pidió, para que la respuesta lo devuelva
     * con los nombres publicados y no con los internos ({@link WhitelistedPageRequest}).
     *
     * @throws InvalidSortPropertyException si alguna propiedad no está publicada
     */
    Pageable apply(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            return pageable;
        }
        Sort translated = Sort.by(sort.stream()
                .map(order -> {
                    String target = properties.get(order.getProperty());
                    if (target == null) {
                        throw new InvalidSortPropertyException(order.getProperty());
                    }
                    return order.withProperty(target);
                })
                .toList());
        return new WhitelistedPageRequest(pageable.getPageNumber(), pageable.getPageSize(), translated, sort);
    }

    /**
     * Se pidió ordenar por una propiedad que el listado no publica.
     */
    @Getter
    static final class InvalidSortPropertyException extends RuntimeException {

        private final String property;

        InvalidSortPropertyException(String property) {
            super("Unknown sort property '%s'".formatted(property));
            this.property = property;
        }
    }
}
