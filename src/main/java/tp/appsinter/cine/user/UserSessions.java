package tp.appsinter.cine.user;

import org.jspecify.annotations.Nullable;

/**
 * Sesiones abiertas de un usuario, que hay que cortar cuando cambian sus credenciales o se lo da de
 * baja.
 *
 * <p>Lo implementa {@code auth}, que depende de {@code user} y no al revés. La llamada es directa y
 * corre dentro de la transacción de quien la hace: si la revocación falla, el cambio de contraseña o
 * la baja se deshacen con ella.
 */
public interface UserSessions {

    void revokeAll(User user);

    /**
     * Revoca todas las sesiones del usuario salvo la que presenta {@code keep}. Si {@code keep} es
     * nulo o desconocido, se revocan todas.
     */
    void revokeAllExcept(User user, @Nullable String keep);
}
