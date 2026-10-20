package tp.appsinter.cine.shared.web;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.shared.error.ErrorCode;

import java.net.URI;
import java.util.Locale;

/**
 * Único lugar donde se arma un cuerpo RFC 9457, tanto para los errores que resuelve el manejador
 * global como para los {@code 401}/{@code 403} que escribe la cadena de seguridad.
 *
 * <p>El estado HTTP sale siempre del {@link ErrorCode}, así el código publicado y el estado de la
 * respuesta no pueden contradecirse. Cada cuerpo lleva un {@code type} estable derivado del código
 * ({@code SEATS_UNAVAILABLE} → {@code <base>seats-unavailable}).
 */
@Component
public class ProblemFactory {

    private final String typeBase;

    public ProblemFactory(ProblemProperties properties) {
        String base = properties.typeBase().toString();
        this.typeBase = base.endsWith("/") ? base : base + "/";
    }

    public ProblemDetail create(ErrorCode code, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        decorate(problem, code);
        return problem;
    }

    /**
     * Completa un cuerpo armado por otro (el framework) con {@code type}, título y {@code code}.
     */
    public void decorate(ProblemDetail problem, ErrorCode code) {
        problem.setType(typeOf(code));
        if (problem.getTitle() == null) {
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            problem.setTitle(status == null ? code.name() : status.getReasonPhrase());
        }
        problem.setProperty("code", code.name());
    }

    private URI typeOf(ErrorCode code) {
        return URI.create(typeBase + code.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
