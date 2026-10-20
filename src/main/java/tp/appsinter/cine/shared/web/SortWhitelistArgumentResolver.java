package tp.appsinter.cine.shared.web;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve los {@link Pageable} anotados con {@link AllowedSort}: delega la lectura de
 * {@code page}/{@code size}/{@code sort} en el resolvedor de Spring Data —que conserva el tope de
 * tamaño configurado y los {@code @PageableDefault}— y después valida y traduce el orden con la
 * lista declarada.
 */
@RequiredArgsConstructor
final class SortWhitelistArgumentResolver implements HandlerMethodArgumentResolver {

    private final PageableHandlerMethodArgumentResolver delegate;
    private final Map<MethodParameter, SortWhitelist> whitelists = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.equals(parameter.getParameterType())
                && parameter.hasParameterAnnotation(AllowedSort.class);
    }

    @Override
    public Pageable resolveArgument(MethodParameter parameter,
                                    @Nullable ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    @Nullable WebDataBinderFactory binderFactory) {
        Pageable pageable = delegate.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        SortWhitelist whitelist = whitelists.computeIfAbsent(parameter,
                key -> SortWhitelist.forParameter(key).orElseThrow());
        return whitelist.apply(pageable);
    }
}
