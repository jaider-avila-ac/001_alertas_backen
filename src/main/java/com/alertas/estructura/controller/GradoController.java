package com.alertas.estructura.controller;

import com.alertas.estructura.dto.GradoResponse;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.shared.dto.CambiarActivoRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ver los grados lo puede cualquiera del colegio (filtros), cambiarlos solo el admin
@RestController
@RequestMapping("/api/v1/grados")
public class GradoController {

    private final EstructuraService estructuraService;

    public GradoController(EstructuraService estructuraService) {
        this.estructuraService = estructuraService;
    }

    @GetMapping
    public List<GradoResponse> listar() {
        return estructuraService.listarGrados();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public GradoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarActivoRequest request) {
        return estructuraService.cambiarEstadoGrado(id, request.activo());
    }
}
