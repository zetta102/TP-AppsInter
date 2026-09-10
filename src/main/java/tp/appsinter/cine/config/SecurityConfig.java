package tp.appsinter.cine.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tp.appsinter.cine.security.JwtAuthenticationFilter;
import tp.appsinter.cine.security.JwtProperties;
import tp.appsinter.cine.security.ProblemDetailAccessDeniedHandler;
import tp.appsinter.cine.security.ProblemDetailAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AdminProperties.class,
        SeatHoldProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // La carta y los metadatos son información pública, igual que la cartelera.
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/metadata/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/movies/**", "/api/screenings/**", "/api/rooms/**", "/api/seats/**")
                        .permitAll()
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
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(corsProperties.allowedHeaders());
        configuration.setExposedHeaders(corsProperties.exposedHeaders());
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }
}
