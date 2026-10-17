package tp.appsinter.cine.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registra {@link SortWhitelistArgumentResolver} antes que el resolvedor de {@code Pageable} de
 * Spring Data, para que un listado anotado con {@link AllowedSort} nunca reciba un orden sin validar.
 */
@Configuration(proxyBeanMethods = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
class SortWebConfig implements WebMvcConfigurer {

    private final PageableHandlerMethodArgumentResolver pageableResolver;

    SortWebConfig(PageableHandlerMethodArgumentResolver pageableResolver) {
        this.pageableResolver = pageableResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SortWhitelistArgumentResolver(pageableResolver));
    }
}
