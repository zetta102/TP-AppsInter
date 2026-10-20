package tp.appsinter.cine.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Año que no está más de {@link #value()} años por delante del actual.
 *
 * <p>El límite depende del año en curso, así que no puede expresarse con {@code @Max}. El año en
 * curso sale del reloj de Bean Validation, que es el de la aplicación. {@code null} es válido.
 */
@Documented
@Constraint(validatedBy = MaxYearsAheadValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface MaxYearsAhead {

    /**
     * Cuántos años por delante del actual se admiten.
     */
    int value();

    String message() default "must not be more than {value} years in the future";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
