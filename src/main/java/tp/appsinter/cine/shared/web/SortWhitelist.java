package tp.appsinter.cine.shared.web;

import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Propiedades por las que un listado admite ordenarse, con su traducción al modelo.
 *
 * <p>Sin esta lista, {@code sort} aceptaba cualquier camino de la entidad: ordenar usuarios por
 * {@code password} revelaba el orden de los hashes, y los nombres internos del modelo pasaban a ser
 * parte del contrato. Cada controlador declara qué publica y con qué nombre; cualquier otra
 * propiedad responde {@code 400 INVALID_SORT_PROPERTY}.
 *
 * @param properties nombre publicado → atributo real de la entidad
 */
record SortWhitelist(Map<String, String> properties) {

    SortWhitelist {
        properties = Map.copyOf(properties);
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
     * Valida y traduce el orden pedido. Sin orden, devuelve la página tal cual.
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
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
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
