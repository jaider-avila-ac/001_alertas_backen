package com.alertas.auth.filter;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.SesionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SesionService sesionService;

    public JwtFilter(JwtService jwtService, SesionService sesionService) {

        this.jwtService = jwtService;
        this.sesionService = sesionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            UsuarioAutenticado usuario = jwtService.leer(token);

            if (usuario != null && sesionService.sigueVigente(usuario)) {
                List<GrantedAuthority> permisos = new ArrayList<>();
                permisos.add(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name()));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(usuario, null, permisos);

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // sin token valido sigue sin autenticar y spring security responde 401 si la ruta lo pide
        chain.doFilter(request, response);
    }
}
