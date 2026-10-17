/**
 * Punto de entrada de la API de reservas de cine.
 *
 * <p>Cada subpaquete agrupa una funcionalidad (entidad, repositorio, servicio, controlador y sus
 * DTOs en {@code dto}); {@code shared} reúne lo que usan varias y {@code config}, la configuración
 * transversal.
 *
 * <p>Los tipos son no nulos salvo que se anoten con {@link org.jspecify.annotations.Nullable}. Las
 * entidades JPA son la excepción: llevan {@link org.jspecify.annotations.NullUnmarked} porque
 * Hibernate las instancia vacías y sus columnas opcionales no están anotadas.
 */
@NullMarked
package tp.appsinter.cine;

import org.jspecify.annotations.NullMarked;
