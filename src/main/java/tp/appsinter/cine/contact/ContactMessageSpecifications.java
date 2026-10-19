package tp.appsinter.cine.contact;

import jakarta.persistence.criteria.JoinType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Filtros componibles para listar mensajes de contacto.
 *
 * <p>«Mis mensajes» y la bandeja de administración comparten estas piezas; la primera siempre
 * añade {@link #ownedBy(String)}. Un criterio ausente devuelve {@link Specification#unrestricted()}
 * en lugar de {@code null}.
 */
public final class ContactMessageSpecifications {

    private static final char ESCAPE = '\\';

    private ContactMessageSpecifications() {
    }

    /**
     * Trae al autor en la misma consulta, para que una página no dispare una consulta por fila.
     *
     * <p>Sólo aplica cuando la consulta no es de conteo: Hibernate rechaza un {@code fetch} dentro
     * de un {@code count}, y el paginado de Spring Data ejecuta ambas.
     */
    public static Specification<ContactMessage> withAuthor() {
        return (root, query, builder) -> {
            if (query != null && !isCountQuery(query.getResultType())) {
                root.fetch("user", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    public static Specification<ContactMessage> ownedBy(String email) {
        return (root, query, builder) -> builder.equal(
                builder.lower(root.get("user").get("email")), email.toLowerCase(Locale.ROOT));
    }

    public static Specification<ContactMessage> hasCategory(@Nullable ContactCategory category) {
        return category == null
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.equal(root.get("category"), category);
    }

    public static Specification<ContactMessage> isRead(@Nullable Boolean read) {
        if (read == null) {
            return Specification.unrestricted();
        }
        return read
                ? (root, query, builder) -> builder.isNotNull(root.get("readAt"))
                : (root, query, builder) -> builder.isNull(root.get("readAt"));
    }

    /**
     * Parte del correo del autor, sin distinguir mayúsculas. Los comodines de {@code LIKE} que
     * escriba el operador se toman literales.
     */
    public static Specification<ContactMessage> emailContains(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + email.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (root, query, builder) ->
                builder.like(builder.lower(root.get("user").get("email")), pattern, ESCAPE);
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }
}
