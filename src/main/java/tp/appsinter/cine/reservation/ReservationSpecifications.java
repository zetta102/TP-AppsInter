package tp.appsinter.cine.reservation;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import tp.appsinter.cine.screening.MovieScreening;

import java.time.OffsetDateTime;
import java.util.Locale;
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
 *
 * <p>Un criterio ausente devuelve {@link Specification#unrestricted()} en lugar de {@code null}:
 * componer con nulo dejó de estar permitido, y el filtro neutro mantiene el encadenado legible sin
 * repartir comprobaciones por cada llamador.
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
                builder.lower(root.get("user").get("email")), email.toLowerCase(Locale.ROOT));
    }

    public static Specification<Reservation> hasStatus(@Nullable ReservationStatus status) {
        return status == null
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Reservation> hasUser(@Nullable UUID userId) {
        return userId == null
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.equal(root.get("user").get("uuid"), userId);
    }

    /**
     * Coincidencia parcial e insensible a mayúsculas sobre el correo del titular, que es como un
     * operador de mostrador busca a un cliente.
     */
    public static Specification<Reservation> emailContains(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + email.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> builder.like(builder.lower(root.get("user").get("email")), pattern);
    }

    public static Specification<Reservation> hasScreening(@Nullable UUID screeningId) {
        return screeningId == null
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.equal(screening(root).get("uuid"), screeningId);
    }

    public static Specification<Reservation> hasMovie(@Nullable UUID movieId) {
        return movieId == null
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.equal(screening(root).get("movie").get("uuid"), movieId);
    }

    /**
     * Rango sobre {@code createdAt}: cuándo se compró la reserva.
     */
    public static Specification<Reservation> createdBetween(@Nullable OffsetDateTime from,
                                                            @Nullable OffsetDateTime to) {
        return between(from, to, root -> root.get("createdAt"));
    }

    /**
     * Rango sobre el {@code startsAt} de la función: cuándo hay que ir al cine. Es el rango que
     * espera un cliente cuando pregunta por «lo que viene».
     */
    public static Specification<Reservation> screeningBetween(@Nullable OffsetDateTime from,
                                                              @Nullable OffsetDateTime to) {
        return between(from, to, root -> screening(root).get("startsAt"));
    }

    public static Specification<Reservation> upcoming(boolean upcomingOnly) {
        return !upcomingOnly
                ? Specification.unrestricted()
                : (root, query, builder) -> builder.greaterThanOrEqualTo(
                screening(root).get("startsAt"), OffsetDateTime.now());
    }

    private static Specification<Reservation> between(@Nullable OffsetDateTime from,
                                                      @Nullable OffsetDateTime to,
                                                      DateAttribute attribute) {
        if (from == null && to == null) {
            return Specification.unrestricted();
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
