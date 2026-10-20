package tp.appsinter.cine.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Nombre de fila de butacas: una o dos letras ({@code A}…{@code Z}, {@code AA}…{@code ZZ}), sin
 * distinguir mayúsculas porque se guardan en mayúscula.
 *
 * <p>Es la única forma que entiende el generador de grillas, que calcula la fila siguiente a la
 * última; una fila {@code 1} o {@code A-1} no tendría siguiente. {@code null} es válido: combinar con
 * {@code @NotBlank} si el campo es obligatorio.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "^[A-Za-z]{1,2}$")
@ReportAsSingleViolation
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface SeatRow {

    String message() default "must be one or two letters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
