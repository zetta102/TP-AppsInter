package tp.appsinter.cine.auth;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tp.appsinter.cine.user.Role;

import java.util.List;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = Role.ADMIN.name();

    /**
     * Lecturas públicas: la carta, los metadatos, la cartelera, las salas y sus butacas. Valen
     * también para {@code HEAD}, que Spring Security no equipara a {@code GET}: sin esa regla un
     * {@code HEAD} caía en «requiere autenticación» y respondía 401 con {@code no-store} en lugar
     * de los encabezados de caché.
     */
    private static final List<String> PUBLIC_READS = List.of(
            "/api/products/**", "/api/metadata/**",
            "/api/movies/**", "/api/screenings/**", "/api/rooms/**", "/api/seats/**");

    /**
     * Rutas abiertas a cualquiera, con o sin token. Las mismas reglas alimentan la cadena de filtros
     * y el documento OpenAPI ({@link SecurityOpenApiCustomizer}), así no pueden contradecirse.
     */
    static final List<PublicRoute> PUBLIC_ROUTES = Stream.concat(
            Stream.of(new PublicRoute(null, "/api/auth/**")),
            PUBLIC_READS.stream().flatMap(pattern -> Stream.of(
                    new PublicRoute(HttpMethod.GET, pattern),
                    new PublicRoute(HttpMethod.HEAD, pattern)))
    ).toList();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    PUBLIC_ROUTES.forEach(route -> auth.requestMatchers(route.method(), route.pattern()).permitAll());
                    auth
                            // Bloquear butacas es cosa de clientes, no de administración, y debe
                            // evaluarse antes de la regla que reserva el resto de /api/screenings.
                            .requestMatchers(HttpMethod.POST, "/api/screenings/*/holds").authenticated()
                            .requestMatchers("/api/movies/**", "/api/screenings/**", "/api/rooms/**",
                                    "/api/seats/**", "/api/inventory/**")
                            .hasRole(ADMIN)
                            // Los endpoints de la cuenta propia deben evaluarse antes de la regla que
                            // reserva el resto de /api/users a administración.
                            .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                            .requestMatchers(HttpMethod.PATCH, "/api/users/me").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/users/me/password").authenticated()
                            .requestMatchers("/api/users/**").hasRole(ADMIN)
                            .requestMatchers(HttpMethod.GET, "/api/reservations/all").hasRole(ADMIN)
                            .requestMatchers("/api/admin/**").hasRole(ADMIN)
                            // Cualquier cuenta puede comprar, bloquear butacas, escribir al cine y
                            // gestionar lo propio.
                            .requestMatchers("/api/reservations/**", "/api/reservations:quote",
                                    "/api/holds/**", "/api/contact-messages/**")
                            .authenticated()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * El filtro es un bean para poder inyectarle dependencias, y Spring Boot registra todo bean
     * {@code Filter} en el contenedor de servlets. Corre sólo dentro de la cadena de seguridad.
     */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        configuration.setExposedHeaders(properties.exposedHeaders());
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Codifica con bcrypt y prefija el algoritmo ({@code {bcrypt}…}), para poder cambiarlo más
     * adelante sin invalidar las contraseñas guardadas. Los hashes sin prefijo —los de los datos de
     * demostración— se siguen verificando como bcrypt.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }

    /**
     * Una regla de acceso público: un método ({@code null} para todos) y un patrón de ruta.
     */
    record PublicRoute(@Nullable HttpMethod method, String pattern) {

        private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

        boolean matches(HttpMethod requestMethod, String path) {
            PathPattern parsed = PARSER.parse(pattern);
            return (method == null || method.equals(requestMethod)) && parsed.matches(PathContainer.parsePath(path));
        }
    }
}
