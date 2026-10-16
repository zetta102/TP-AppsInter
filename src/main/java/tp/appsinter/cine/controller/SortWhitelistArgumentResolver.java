package tp.appsinter.cine.controller;

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
 * tamaño configurado— y después valida y traduce el orden con la lista declarada.
 */
final class SortWhitelistArgumentResolver implements HandlerMethodArgumentResolver {

    private final PageableHandlerMethodArgumentResolver delegate;
    private final Map<MethodParameter, SortWhitelist> whitelists = new ConcurrentHashMap<>();

    SortWhitelistArgumentResolver(PageableHandlerMethodArgumentResolver delegate) {
        this.delegate = delegate;
    }

    static SortWhitelist whitelistOf(AllowedSort allowed) {
        SortWhitelist whitelist = SortWhitelist.of(allowed.value());
        for (AllowedSort.Alias alias : allowed.aliases()) {
            whitelist = whitelist.alias(alias.name(), alias.property());
        }
        return whitelist;
    }

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
                key -> whitelistOf(key.getParameterAnnotation(AllowedSort.class)));
        return whitelist.apply(pageable);
    }
}
