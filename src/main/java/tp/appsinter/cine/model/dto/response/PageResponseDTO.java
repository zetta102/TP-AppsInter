package tp.appsinter.cine.model.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope. Spring's own {@code Page} serialization is not part of its public
 * contract, so listings wrap results in this record instead.
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

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static <E, T> PageResponseDTO<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponseDTO<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getSort().toString()
        );
    }
}
