package tp.appsinter.cine.shared.web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Página cuyo orden ya pasó por {@link SortWhitelist}: ordena por los atributos reales de la
 * entidad, pero recuerda los nombres publicados con que se pidió.
 *
 * <p>Spring Data devuelve la misma instancia en {@code Page.getPageable()}, y así
 * {@link tp.appsinter.cine.shared.dto.response.PageResponseDTO} puede informar el orden con los
 * nombres del contrato ({@code userEmail: ASC}) en lugar de los del modelo ({@code user.email: ASC}).
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public final class WhitelistedPageRequest extends PageRequest {

    private final Sort publishedSort;

    WhitelistedPageRequest(int page, int size, Sort entitySort, Sort publishedSort) {
        super(page, size, entitySort);
        this.publishedSort = publishedSort;
    }
}
