package tp.appsinter.cine.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tp.appsinter.cine.controller.ProblemFactory;
import tp.appsinter.cine.exception.ErrorCode;

import java.io.IOException;
import java.net.URI;

/**
 * Escribe un cuerpo RFC 7807 para los fallos de seguridad, de modo que todos los errores de la API
 * compartan un mismo sobre, incluidos los {@code 401} y {@code 403} que produce la cadena de
 * filtros antes de llegar a un controlador.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailWriter {

    /**
     * Atributo de petición con el que un filtro previo puede afinar el código de error publicado.
     * Lo usa {@link JwtAuthenticationFilter} para distinguir un token vencido de uno inválido.
     */
    public static final String ERROR_CODE_ATTRIBUTE = "cine.errorCode";

    private final ObjectMapper objectMapper;
    private final ProblemFactory problems;

    void write(HttpServletRequest request,
               HttpServletResponse response,
               HttpStatus status,
               String detail,
               ErrorCode defaultCode) throws IOException {
        ProblemDetail problem = problems.create(status, resolveCode(request, defaultCode), detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private ErrorCode resolveCode(HttpServletRequest request, ErrorCode defaultCode) {
        return request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof ErrorCode override ? override : defaultCode;
    }
}
