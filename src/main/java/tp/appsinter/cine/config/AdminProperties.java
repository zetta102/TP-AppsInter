package tp.appsinter.cine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional first-admin seed. Both values must be supplied (normally via the
 * {@code CINE_ADMIN_EMAIL} / {@code CINE_ADMIN_PASSWORD} environment variables) for seeding to run;
 * no credential is ever committed to the repository.
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
