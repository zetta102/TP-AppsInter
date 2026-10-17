package tp.appsinter.cine.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.exception.ErrorCode;

import java.net.URI;
import java.util.Locale;

/**
 * Único lugar donde se arma un cuerpo RFC 7807, tanto para los errores que resuelve el manejador
 * global como para los {@code 401}/{@code 403} que escribe la cadena de seguridad.
 *
 * <p>Cada cuerpo lleva un {@code type} estable derivado del código ({@code SEATS_UNAVAILABLE} →
 * {@code <base>seats-unavailable}). La URI identifica el tipo de problema; no tiene por qué
 * resolverse a una página.
 */
@Component
public class ProblemFactory {

    public static final String DEFAULT_TYPE_BASE = "https://cine.example/problems/";

    private final String typeBase;

    public ProblemFactory(@Value("${cine.problems.type-base:" + DEFAULT_TYPE_BASE + "}") String typeBase) {
        this.typeBase = typeBase.endsWith("/") ? typeBase : typeBase + "/";
    }

    /**
     * Cuerpo con el estado registrado para el código.
     */
    public ProblemDetail create(ErrorCode code, @Nullable String detail) {
        return create(code.status(), code, detail);
    }

    /**
     * Cuerpo con un estado explícito, para los pocos casos en que el estado lo decide quien escribe
     * la respuesta y no el código.
     */
    public ProblemDetail create(HttpStatusCode status, ErrorCode code, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
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

    public URI typeOf(ErrorCode code) {
        return URI.create(typeBase + code.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
