package com.alertas.superadmin.controller;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.superadmin.dto.CambiarContrasenaRequest;
import com.alertas.superadmin.dto.LoginSuperadminRequest;
import com.alertas.superadmin.dto.LoginSuperadminResponse;
import com.alertas.superadmin.dto.SuperadminResponse;
import com.alertas.superadmin.service.SuperadminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/superadmin/auth")
public class SuperadminAuthController {

    private final SuperadminService superadminService;

    public SuperadminAuthController(SuperadminService superadminService) {
        this.superadminService = superadminService;
    }

    @PostMapping("/login")
    public LoginSuperadminResponse login(@Valid @RequestBody LoginSuperadminRequest request, HttpServletRequest http) {
        return superadminService.login(request, http.getRemoteAddr());
    }

    @GetMapping("/yo")
    public SuperadminResponse yo() {
        return superadminService.buscar(UsuarioAutenticado.actual().id());
    }

    @PutMapping("/contrasena")
    public LoginSuperadminResponse cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request) {
        return superadminService.cambiarContrasena(UsuarioAutenticado.actual().id(), request);
    }
}
