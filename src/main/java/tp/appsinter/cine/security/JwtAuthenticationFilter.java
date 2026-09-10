package tp.appsinter.cine.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tp.appsinter.cine.exception.ErrorCode;

import java.io.IOException;

/**
 * Autentica cada petición a partir del token portador, si lo hay. Nunca corta la cadena: un token
 * ausente o inválido simplemente deja la petición anónima, y son las reglas de autorización las que
 * deciden si eso alcanza.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtService.ParsedAccessToken parsed = jwtService.parse(header.substring(BEARER_PREFIX.length()));
        if (parsed.expired()) {
            // El punto de entrada de seguridad lo lee para responder TOKEN_EXPIRED en lugar de
            // INVALID_CREDENTIALS, que es lo que le diría al cliente que vuelva a iniciar sesión.
            request.setAttribute(ProblemDetailWriter.ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
        }
        parsed.claimsIfValid()
                .map(Claims::getSubject)
                .ifPresent(email -> authenticate(email, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(String email, HttpServletRequest request) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
