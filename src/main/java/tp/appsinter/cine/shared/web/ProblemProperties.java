package tp.appsinter.cine.shared.web;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * Configuración de los cuerpos de error.
 *
 * @param typeBase prefijo del campo {@code type}: {@code <prefijo><código-en-kebab-case>}. La URI
 *                 identifica el tipo de problema; no tiene por qué resolverse a una página.
 */
@Validated
@ConfigurationProperties(prefix = "cine.problems")
public record ProblemProperties(@DefaultValue(ProblemProperties.DEFAULT_TYPE_BASE) @NotNull URI typeBase) {

    public static final String DEFAULT_TYPE_BASE = "https://cine.example/problems/";

    public static ProblemProperties defaults() {
        return new ProblemProperties(URI.create(DEFAULT_TYPE_BASE));
    }
}
