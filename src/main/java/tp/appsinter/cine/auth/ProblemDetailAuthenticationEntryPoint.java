package tp.appsinter.cine.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.shared.error.ErrorCode;

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
        // Una familia revocada por reutilización no es lo mismo que unas credenciales cualquiera: el
        // cliente tiene que dejar de reintentar y volver a pedir el inicio de sesión.
        if (authException instanceof RefreshTokenReusedException) {
            writer.write(request, response, HttpStatus.UNAUTHORIZED,
                    authException.getMessage(), ErrorCode.TOKEN_REVOKED);
            return;
        }
        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource", ErrorCode.INVALID_CREDENTIALS);
    }
}
