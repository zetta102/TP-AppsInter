package tp.appsinter.cine.user;

import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import tp.appsinter.cine.shared.persistence.CriteriaSupport;

/**
 * Filtros combinables de la búsqueda de usuarios de administración.
 *
 * <p>Un filtro ausente no agrega condición: a diferencia de un JPQL con {@code :param is null},
 * nunca envía parámetros sin tipo a PostgreSQL. Las búsquedas por texto son «contiene» sin
 * distinguir mayúsculas, y los comodines de {@code LIKE} que escriba el operador se toman literales.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /**
     * Las cuentas dadas de baja nunca aparecen en la administración.
     */
    public static Specification<User> active() {
        return (root, _, builder) -> builder.isNull(root.get("deletedAt"));
    }

    public static Specification<User> hasRole(@Nullable Role role) {
        return role == null
                ? Specification.unrestricted()
                : (root, _, builder) -> builder.equal(root.get("role"), role);
    }

    public static Specification<User> emailContains(@Nullable String email) {
        String pattern = CriteriaSupport.containsPattern(email);
        return pattern == null
                ? Specification.unrestricted()
                : (root, _, builder) -> CriteriaSupport.like(builder, root.get("email"), pattern);
    }

    /**
     * Coincide con el nombre, el apellido o el nombre completo ({@code "ada love"} encuentra a
     * «Ada Lovelace»).
     */
    public static Specification<User> nameContains(@Nullable String name) {
        String pattern = CriteriaSupport.containsPattern(name);
        if (pattern == null) {
            return Specification.unrestricted();
        }
        return (root, _, builder) -> {
            Expression<String> firstName = root.get("firstName");
            Expression<String> lastName = root.get("lastName");
            Expression<String> fullName = builder.concat(builder.concat(firstName, " "), lastName);
            return builder.or(
                    CriteriaSupport.like(builder, firstName, pattern),
                    CriteriaSupport.like(builder, lastName, pattern),
                    CriteriaSupport.like(builder, fullName, pattern));
        };
    }
}
