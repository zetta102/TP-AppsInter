package tp.appsinter.cine.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.shared.error.ErrorCode;

import java.io.IOException;

/**
 * Traduce un rechazo por rol insuficiente a un cuerpo RFC 9457 con código {@code FORBIDDEN}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, "Access denied", ErrorCode.FORBIDDEN);
    }
}
