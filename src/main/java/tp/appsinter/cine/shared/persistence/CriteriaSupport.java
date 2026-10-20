package tp.appsinter.cine.shared.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Piezas comunes de los filtros con Criteria de los listados.
 */
public final class CriteriaSupport {

    private static final char ESCAPE = '\\';

    private CriteriaSupport() {
    }

    /**
     * Patrón «contiene» sin distinguir mayúsculas para {@link #like}, o {@code null} si no hay nada
     * que buscar. Los comodines de {@code LIKE} que escriba quien busca se toman literales: buscar
     * {@code _} encuentra guiones bajos, no cualquier carácter.
     */
    public static @Nullable String containsPattern(@Nullable String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Compara sin distinguir mayúsculas contra un patrón de {@link #containsPattern}.
     */
    public static Predicate like(CriteriaBuilder builder, Expression<String> value, String pattern) {
        return builder.like(builder.lower(value), pattern, ESCAPE);
    }

    /**
     * Indica si la consulta es el conteo que Spring Data ejecuta junto con cada página. Hibernate
     * rechaza un {@code fetch} dentro de un {@code count}, así que los filtros que traen asociaciones
     * lo saltean en ese caso.
     */
    public static boolean isCountQuery(@Nullable CriteriaQuery<?> query) {
        if (query == null) {
            return true;
        }
        Class<?> resultType = query.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }
}
