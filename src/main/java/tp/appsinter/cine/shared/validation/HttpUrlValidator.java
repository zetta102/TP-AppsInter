package tp.appsinter.cine.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Acepta sólo lo que {@link URI} interpreta como una dirección absoluta con esquema {@code http} o
 * {@code https}, servidor no vacío y sin espacios, y que además puede convertirse en {@link java.net.URL}.
 */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    private static final Set<String> SCHEMES = Set.of("http", "https");

    public static boolean isValid(@Nullable String value) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                    || host == null || host.isEmpty()) {
                return false;
            }
            uri.toURL();
            return true;
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
        return isValid(value);
    }
}
