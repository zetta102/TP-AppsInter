package tp.appsinter.cine.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tp.appsinter.cine.shared.error.ErrorCode;
import tp.appsinter.cine.shared.web.ProblemFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Escribe un cuerpo RFC 9457 para los fallos de seguridad, de modo que todos los errores de la API
 * compartan un mismo sobre, incluidos los {@code 401} y {@code 403} que produce la cadena de
 * filtros antes de llegar a un controlador. El estado sale del código, igual que en el manejador
 * global.
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
               @Nullable String detail,
               ErrorCode defaultCode) throws IOException {
        ProblemDetail problem = problems.create(resolveCode(request, defaultCode), detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /**
     * El código que dejó un filtro previo sólo afina uno del mismo estado: un token vencido sigue
     * siendo un {@code 401}, y nunca convierte un {@code 403} en otra cosa.
     */
    private ErrorCode resolveCode(HttpServletRequest request, ErrorCode defaultCode) {
        return request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof ErrorCode override
                && override.status() == defaultCode.status() ? override : defaultCode;
    }
}
