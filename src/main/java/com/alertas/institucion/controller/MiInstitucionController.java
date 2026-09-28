package com.alertas.institucion.controller;

import com.alertas.institucion.dto.InstitucionPublicaResponse;
import com.alertas.institucion.dto.InstitucionResponse;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.shared.interceptor.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// la institucion vista desde adentro (usuarios del colegio) y desde el enlace publico
@RestController
public class MiInstitucionController {

    private final InstitucionService institucionService;

    public MiInstitucionController(InstitucionService institucionService) {
        this.institucionService = institucionService;
    }

    // la pantalla de login pide esto para mostrar el nombre. si esta inactiva el interceptor responde 404
    @GetMapping("/api/v1/public/{slug}")
    public InstitucionPublicaResponse publica() {
        return institucionService.buscarPublica(TenantContext.getInstitucionId());
    }

    @GetMapping("/api/v1/institucion")
    @PreAuthorize("hasRole('ADMIN')")
    public InstitucionResponse miInstitucion() {
        return institucionService.miInstitucion();
    }

    @PatchMapping("/api/v1/institucion/acceso-estudiantes")
    @PreAuthorize("hasRole('ADMIN')")
    public InstitucionResponse cambiarAccesoEstudiantes(@Valid @RequestBody CambiarActivoRequest request) {
        return institucionService.cambiarAccesoEstudiantes(request.activo());
    }
}
