package tp.appsinter.cine.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.error.HasErrorCode;

import java.io.IOException;

/**
 * Sustituye a {@code HttpStatusEntryPoint} para que las peticiones sin autenticar reciban un cuerpo
 * en lugar de un 401 vacío.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Una excepción con código propio (una sesión revocada, por ejemplo) no es lo mismo que unas
        // credenciales cualquiera: el cliente tiene que saber que debe volver a iniciar sesión.
        if (authException instanceof HasErrorCode coded) {
            writer.write(request, response, authException.getMessage(), coded.getCode());
            return;
        }
        writer.write(request, response,
                "Authentication is required to access this resource", ErrorCode.INVALID_CREDENTIALS);
    }
}
