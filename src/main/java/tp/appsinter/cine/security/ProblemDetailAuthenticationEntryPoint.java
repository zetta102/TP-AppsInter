package tp.appsinter.cine.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces {@code HttpStatusEntryPoint} so unauthenticated requests get a body instead of a bare 401.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    @Override
    public void commence(@NonNull HttpServletRequest request,
                         @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) throws IOException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource");
    }
}
