package com.alertas.config;

import com.alertas.auth.filter.JwtFilter;
import com.alertas.shared.exception.ErrorResponse;
import com.alertas.shared.idempotencia.IdempotenciaInterceptor;
import com.alertas.shared.interceptor.TenantInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper mapper;

    public SecurityConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // spring security 6 solo se configura con lambdas, por eso aqui si se usan
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);

        // toma el bean corsConfigurationSource de abajo
        http.cors(Customizer.withDefaults());

        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health", "/error").permitAll()
                // el websocket se autentica con un ticket de un solo uso (ver notificacion/config)
                .requestMatchers("/ws/notificaciones", "/ws/superadmin").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/superadmin/auth/login").permitAll()
                .requestMatchers("/api/v1/superadmin/**").hasRole("SUPERADMIN")
                .requestMatchers("/api/v1/**").hasAnyRole("ADMIN", "PSICORIENTADOR", "DOCENTE", "ESTUDIANTE")
                .anyRequest().denyAll());

        http.exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) ->
                        escribirError(response, HttpStatus.UNAUTHORIZED, "Tu sesion vencio, vuelve a iniciar sesion"))
                .accessDeniedHandler((request, response, ex) ->
                        escribirError(response, HttpStatus.FORBIDDEN, "No tienes permiso para esta accion")));

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // el filtro ya va dentro de spring security, esto evita que spring boot lo registre otra vez
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistro(JwtFilter jwtFilter) {

        FilterRegistrationBean<JwtFilter> registro = new FilterRegistrationBean<>(jwtFilter);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origenes}") String origenes) {

        List<String> permitidos = new ArrayList<>();

        for (String origen : origenes.split(",")) {
            permitidos.add(origen.trim());
        }

        List<String> cabeceras = new ArrayList<>();
        cabeceras.add("Authorization");
        cabeceras.add("Content-Type");
        cabeceras.add(TenantInterceptor.CABECERA_SLUG);
        cabeceras.add(IdempotenciaInterceptor.CABECERA);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(permitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(cabeceras);
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void escribirError(HttpServletResponse response, HttpStatus status, String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(message));
    }
}
