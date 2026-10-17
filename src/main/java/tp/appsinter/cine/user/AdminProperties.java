package tp.appsinter.cine.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Semilla opcional del primer administrador. Para que la siembra se ejecute hay que dar los dos
 * valores, normalmente por las variables de entorno {@code CINE_ADMIN_EMAIL} /
 * {@code CINE_ADMIN_PASSWORD}: ninguna credencial se versiona en el repositorio.
 */
@ConfigurationProperties(prefix = "cine.admin")
public record AdminProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String password,
        @DefaultValue("Cine") String firstName,
        @DefaultValue("Administrator") String lastName
) {

    public boolean configured() {
        return !email.isBlank() && !password.isBlank();
    }
}
