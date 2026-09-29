package com.alertas.miproceso.controller;

import com.alertas.miproceso.dto.MiProcesoResponse;
import com.alertas.miproceso.service.MiProcesoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MiProcesoController {

    private final MiProcesoService miProcesoService;

    public MiProcesoController(MiProcesoService miProcesoService) {
        this.miProcesoService = miProcesoService;
    }

    @GetMapping("/api/v1/mi-proceso")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public MiProcesoResponse miProceso() {
        return miProcesoService.miProceso();
    }
}
