package com.alertas.auth.controller;

import com.alertas.auth.dto.CambiarContrasenaRequest;
import com.alertas.auth.dto.LoginRequest;
import com.alertas.auth.dto.LoginResponse;
import com.alertas.auth.dto.PerfilResponse;
import com.alertas.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // solo se entra por el enlace del colegio: el slug de la url decide en que institucion se busca
    @PostMapping("/api/v1/public/{slug}/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/api/v1/auth/salir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir() {
        authService.salir();
    }

    @GetMapping("/api/v1/auth/yo")
    public PerfilResponse yo() {
        return authService.perfil();
    }

    @PutMapping("/api/v1/auth/contrasena")
    public LoginResponse cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request) {
        return authService.cambiarContrasena(request);
    }
}
