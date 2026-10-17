package tp.appsinter.cine.validation;

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
 * Dirección absoluta {@code http}/{@code https} que un navegador puede cargar.
 *
 * <p>Una expresión regular como {@code ^https?://\S+$} deja pasar cualquier cosa después del
 * esquema; acá la cadena se interpreta de verdad (ver {@link HttpUrlValidator}). {@code null} es
 * válido: combinar con {@code @NotNull} si el campo es obligatorio.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface HttpUrl {

    String message() default "must be an absolute http(s) URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
