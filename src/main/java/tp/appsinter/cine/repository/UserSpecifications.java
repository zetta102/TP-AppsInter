package tp.appsinter.cine.repository;

import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;

import java.util.Locale;

/**
 * Filtros combinables de la búsqueda de usuarios de administración.
 *
 * <p>Un filtro ausente no agrega condición: a diferencia de un JPQL con {@code :param is null},
 * nunca envía parámetros sin tipo a PostgreSQL. Las búsquedas por texto son «contiene» sin
 * distinguir mayúsculas, y los comodines de {@code LIKE} que escriba el operador se toman literales.
 */
public final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    /**
     * Las cuentas dadas de baja nunca aparecen en la administración.
     */
    public static Specification<User> active() {
        return (root, query, builder) -> builder.isNull(root.get("deletedAt"));
    }

    public static Specification<User> hasRole(@Nullable Role role) {
        return role == null ? always() : (root, query, builder) -> builder.equal(root.get("role"), role);
    }

    public static Specification<User> emailContains(@Nullable String email) {
        String pattern = containsPattern(email);
        return pattern == null
                ? always()
                : (root, query, builder) -> builder.like(builder.lower(root.get("email")), pattern, ESCAPE);
    }

    /**
     * Coincide con el nombre, el apellido o el nombre completo ({@code "ada love"} encuentra a
     * «Ada Lovelace»).
     */
    public static Specification<User> nameContains(@Nullable String name) {
        String pattern = containsPattern(name);
        if (pattern == null) {
            return always();
        }
        return (root, query, builder) -> {
            Expression<String> firstName = builder.lower(root.get("firstName"));
            Expression<String> lastName = builder.lower(root.get("lastName"));
            Expression<String> fullName = builder.concat(builder.concat(firstName, " "), lastName);
            return builder.or(
                    builder.like(firstName, pattern, ESCAPE),
                    builder.like(lastName, pattern, ESCAPE),
                    builder.like(fullName, pattern, ESCAPE));
        };
    }

    private static @Nullable String containsPattern(@Nullable String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static Specification<User> always() {
        return (root, query, builder) -> builder.conjunction();
    }
}
