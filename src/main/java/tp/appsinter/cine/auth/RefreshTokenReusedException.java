package tp.appsinter.cine.auth;

import org.springframework.security.core.AuthenticationException;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.error.HasErrorCode;

/**
 * Un token de refresco se presentó después de haber sido rotado y fuera de la ventana de gracia.
 *
 * <p>Se distingue de unas credenciales cualquiera porque el cliente necesita saber que la sesión fue
 * revocada — y no simplemente rechazada — para dejar de reintentar y volver a pedir el inicio de
 * sesión. Se traduce en {@code 401} con {@code code: "TOKEN_REVOKED"}.
 */
public class RefreshTokenReusedException extends AuthenticationException implements HasErrorCode {

    public RefreshTokenReusedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.TOKEN_REVOKED;
    }
}
