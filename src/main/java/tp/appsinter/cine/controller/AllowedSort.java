package tp.appsinter.cine.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Propiedades por las que un listado admite ordenarse, declaradas sobre su parámetro
 * {@link org.springframework.data.domain.Pageable}.
 *
 * <p>Es la única fuente de la lista: {@link SortWhitelistArgumentResolver} la aplica antes de que el
 * controlador reciba la página, y la documentación OpenAPI la publica como
 * {@code x-sort-properties} en el parámetro {@code sort}. Cualquier otra propiedad responde
 * {@code 400 INVALID_SORT_PROPERTY}.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedSort {

    /**
     * Propiedades publicadas con el mismo nombre que el atributo de la entidad.
     */
    String[] value() default {};

    /**
     * Propiedades publicadas con un nombre distinto del atributo de la entidad.
     */
    Alias[] aliases() default {};

    @Documented
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Alias {

        /**
         * Nombre publicado.
         */
        String name();

        /**
         * Atributo (o camino) real de la entidad.
         */
        String property();
    }
}
