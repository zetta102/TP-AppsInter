package tp.appsinter.cine.contact;

import jakarta.persistence.criteria.JoinType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import tp.appsinter.cine.shared.persistence.CriteriaSupport;

import java.util.Locale;

/**
 * Filtros componibles para listar mensajes de contacto.
 *
 * <p>«Mis mensajes» y la bandeja de administración comparten estas piezas; la primera siempre
 * añade {@link #ownedBy(String)}. Un criterio ausente devuelve {@link Specification#unrestricted()}
 * en lugar de {@code null}.
 */
public final class ContactMessageSpecifications {

    private ContactMessageSpecifications() {
    }

    /**
     * Trae al autor en la misma consulta, para que una página no dispare una consulta por fila.
     */
    public static Specification<ContactMessage> withAuthor() {
        return (root, query, builder) -> {
            if (!CriteriaSupport.isCountQuery(query)) {
                root.fetch("user", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    public static Specification<ContactMessage> ownedBy(String email) {
        return (root, _, builder) -> builder.equal(
                builder.lower(root.get("user").get("email")), email.toLowerCase(Locale.ROOT));
    }

    public static Specification<ContactMessage> hasCategory(@Nullable ContactCategory category) {
        return category == null
                ? Specification.unrestricted()
                : (root, _, builder) -> builder.equal(root.get("category"), category);
    }

    public static Specification<ContactMessage> isRead(@Nullable Boolean read) {
        if (read == null) {
            return Specification.unrestricted();
        }
        return read
                ? (root, _, builder) -> builder.isNotNull(root.get("readAt"))
                : (root, _, builder) -> builder.isNull(root.get("readAt"));
    }

    /**
     * Parte del correo del autor, sin distinguir mayúsculas. Los comodines de {@code LIKE} que
     * escriba el operador se toman literales.
     */
    public static Specification<ContactMessage> emailContains(@Nullable String email) {
        String pattern = CriteriaSupport.containsPattern(email);
        return pattern == null
                ? Specification.unrestricted()
                : (root, _, builder) -> CriteriaSupport.like(builder, root.get("user").get("email"), pattern);
    }
}
