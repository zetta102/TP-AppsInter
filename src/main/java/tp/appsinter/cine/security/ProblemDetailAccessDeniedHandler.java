package tp.appsinter.cine.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.exception.ErrorCode;

import java.io.IOException;

/**
 * Traduce un rechazo por rol insuficiente a un cuerpo RFC 7807 con código {@code FORBIDDEN}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "Access denied", ErrorCode.FORBIDDEN);
    }
}
