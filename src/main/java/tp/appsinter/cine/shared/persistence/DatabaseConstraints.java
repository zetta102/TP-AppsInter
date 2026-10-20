package tp.appsinter.cine.shared.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Lee qué restricción de la base rechazó una escritura.
 *
 * <p>Spring envuelve el error en una {@code DataIntegrityViolationException} que no dice cuál fue;
 * la causa de Hibernate sí. Los nombres son los de {@code V1__schema.sql}, en minúsculas.
 */
public final class DatabaseConstraints {

    private DatabaseConstraints() {
    }

    /**
     * La violación de Hibernate que hay en la cadena de causas, si la hay.
     */
    public static Optional<ConstraintViolationException> violation(Throwable exception) {
        return Optional.ofNullable(findCause(exception, ConstraintViolationException.class));
    }

    /**
     * Nombre en minúsculas de la restricción violada, o vacío si la causa no lo informa.
     */
    public static String violatedConstraint(Throwable exception) {
        return violation(exception)
                .map(ConstraintViolationException::getConstraintName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .orElse("");
    }

    public static boolean violates(Throwable exception, String constraint) {
        return violatedConstraint(exception).equals(constraint);
    }

    public static <T extends Throwable> @Nullable T findCause(Throwable exception, Class<T> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
