package tp.appsinter.cine.auth;

import org.springframework.security.core.AuthenticationException;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.error.HasErrorCode;

/**
 * Se presentó un token de refresco revocado: porque la sesión se cerró, porque se cambió la
 * contraseña o se dio de baja la cuenta, o porque se lo reutilizó después de rotarlo y fuera de la
 * ventana de gracia.
 *
 * <p>Se distingue de unas credenciales cualquiera porque el cliente necesita saber que la sesión fue
 * revocada —y no simplemente rechazada— para dejar de reintentar y volver a pedir el inicio de
 * sesión. Se traduce en {@code 401} con {@code code: "TOKEN_REVOKED"}.
 */
public class RefreshTokenRevokedException extends AuthenticationException implements HasErrorCode {

    public RefreshTokenRevokedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.TOKEN_REVOKED;
    }
}
