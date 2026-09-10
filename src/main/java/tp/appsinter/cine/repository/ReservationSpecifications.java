package tp.appsinter.cine.repository;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import tp.appsinter.cine.model.entities.MovieScreening;
import tp.appsinter.cine.model.entities.Reservation;
import tp.appsinter.cine.model.utils.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros componibles para listar reservas.
 *
 * <p>La consola de administración y la pantalla «mis reservas» comparten estas piezas: la única
 * diferencia entre ambas es que la segunda siempre añade {@link #ownedBy(String)}. Reunirlas acá
 * evita que las dos listas terminen soportando filtros distintos.
 *
 * <p>Todas las asociaciones que se navegan ({@code user}, {@code movieScreening} y las suyas) son
 * obligatorias, así que la navegación por camino produce un {@code inner join} correcto y no hace
 * falta construir los {@code join} a mano.
 */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    /**
     * Trae en la misma consulta las asociaciones que el DTO de respuesta siempre necesita, para que
     * una página no dispare una consulta por fila.
     *
     * <p>Sólo aplica cuando la consulta no es de conteo: Hibernate rechaza un {@code fetch} dentro
     * de un {@code count}, y el paginado de Spring Data ejecuta ambas.
     */
    public static Specification<Reservation> withAssociations() {
        return (root, query, builder) -> {
            if (query != null && !isCountQuery(query.getResultType())) {
                root.fetch("user", JoinType.LEFT);
                Fetch<?, ?> screening = root.fetch("movieScreening", JoinType.LEFT);
                screening.fetch("movie", JoinType.LEFT);
                screening.fetch("screenRoom", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }

    public static Specification<Reservation> ownedBy(String email) {
        return (root, query, builder) -> builder.equal(
                builder.lower(root.get("user").get("email")), email.toLowerCase());
    }

    public static Specification<Reservation> hasStatus(ReservationStatus status) {
        return status == null ? always() : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Reservation> hasUser(UUID userId) {
        return userId == null
                ? always()
                : (root, query, builder) -> builder.equal(root.get("user").get("uuid"), userId);
    }

    /**
     * Coincidencia parcial e insensible a mayúsculas sobre el correo del titular, que es como un
     * operador de mostrador busca a un cliente.
     */
    public static Specification<Reservation> emailContains(String email) {
        if (email == null || email.isBlank()) {
            return always();
        }
        String pattern = "%" + email.trim().toLowerCase() + "%";
        return (root, query, builder) -> builder.like(builder.lower(root.get("user").get("email")), pattern);
    }

    public static Specification<Reservation> hasScreening(UUID screeningId) {
        return screeningId == null
                ? always()
                : (root, query, builder) -> builder.equal(screening(root).get("uuid"), screeningId);
    }

    public static Specification<Reservation> hasMovie(UUID movieId) {
        return movieId == null
                ? always()
                : (root, query, builder) -> builder.equal(screening(root).get("movie").get("uuid"), movieId);
    }

    /**
     * Rango sobre {@code createdAt}: cuándo se compró la reserva.
     */
    public static Specification<Reservation> createdBetween(OffsetDateTime from, OffsetDateTime to) {
        return between(from, to, root -> root.get("createdAt"));
    }

    /**
     * Rango sobre el {@code startsAt} de la función: cuándo hay que ir al cine. Es el rango que
     * espera un cliente cuando pregunta por «lo que viene».
     */
    public static Specification<Reservation> screeningBetween(OffsetDateTime from, OffsetDateTime to) {
        return between(from, to, root -> screening(root).get("startsAt"));
    }

    public static Specification<Reservation> upcoming(boolean upcomingOnly) {
        return !upcomingOnly
                ? always()
                : (root, query, builder) -> builder.greaterThanOrEqualTo(
                screening(root).get("startsAt"), OffsetDateTime.now());
    }

    /**
     * Filtro neutro.
     *
     * <p>Los criterios ausentes devuelven esto en lugar de {@code null}: componer con nulo dejó de
     * estar permitido, y devolver una condición siempre verdadera mantiene el encadenado legible sin
     * repartir comprobaciones por cada llamador.
     */
    private static Specification<Reservation> always() {
        return (root, query, builder) -> builder.conjunction();
    }

    private static Specification<Reservation> between(OffsetDateTime from,
                                                      OffsetDateTime to,
                                                      DateAttribute attribute) {
        if (from == null && to == null) {
            return always();
        }
        return (root, query, builder) -> {
            Path<OffsetDateTime> path = attribute.of(root);
            if (from != null && to != null) {
                return builder.between(path, from, to);
            }
            return from != null
                    ? builder.greaterThanOrEqualTo(path, from)
                    : builder.lessThanOrEqualTo(path, to);
        };
    }

    private static Path<MovieScreening> screening(Root<Reservation> root) {
        return root.get("movieScreening");
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    /**
     * Selector de la fecha sobre la que aplica un rango, para no repetir la lógica de límites.
     */
    @FunctionalInterface
    private interface DateAttribute {
        Path<OffsetDateTime> of(Root<Reservation> root);
    }
}
