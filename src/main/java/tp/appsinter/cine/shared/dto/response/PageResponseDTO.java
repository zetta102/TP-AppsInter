package tp.appsinter.cine.shared.dto.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import tp.appsinter.cine.shared.web.WhitelistedPageRequest;

import java.util.List;
import java.util.function.Function;

/**
 * Sobre de paginación estable. La serialización de {@code Page} no forma parte del contrato público
 * de Spring, así que los listados envuelven sus resultados en este registro.
 *
 * @param sort orden aplicado, con los nombres publicados por el listado ({@code "createdAt: DESC"}),
 *             o {@code "UNSORTED"}
 */
public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort
) {

    public static <E, T> PageResponseDTO<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page, page.getContent().stream().map(mapper).toList());
    }

    /**
     * Variante para cuando el contenido ya fue convertido en lote, por ejemplo porque hizo falta
     * una consulta agregada que cubre toda la página de una vez.
     */
    public static <E, T> PageResponseDTO<T> from(Page<E> page, List<T> content) {
        return new PageResponseDTO<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                publishedSort(page).toString()
        );
    }

    private static Sort publishedSort(Page<?> page) {
        return page.getPageable() instanceof WhitelistedPageRequest whitelisted
                ? whitelisted.getPublishedSort()
                : page.getSort();
    }
}
