package com.alertas.usuario.controller;

import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.usuario.dto.EstadoMasivoRequest;
import com.alertas.usuario.dto.EstadoMasivoResponse;
import com.alertas.usuario.dto.UsuarioEstadoResponse;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// lo que el administrador hace sobre los usuarios de su institucion
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/{id}/restablecer-contrasena")
    public UsuarioEstadoResponse restablecerContrasena(@PathVariable Long id) {

        Usuario usuario = usuarioService.restablecerContrasenaPorAdmin(id);
        return UsuarioEstadoResponse.desde(usuario);
    }

    @PatchMapping("/{id}/estado")
    public UsuarioEstadoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarActivoRequest request) {

        Usuario usuario = usuarioService.cambiarEstadoPorAdmin(id, request.activo());
        return UsuarioEstadoResponse.desde(usuario);
    }

    @PatchMapping("/estado-masivo")
    public EstadoMasivoResponse cambiarEstadoMasivo(@Valid @RequestBody EstadoMasivoRequest request) {

        int afectados = usuarioService.cambiarEstadoMasivo(request.rol(), request.usuarioIds(), request.activo());
        return new EstadoMasivoResponse(afectados);
    }
}
