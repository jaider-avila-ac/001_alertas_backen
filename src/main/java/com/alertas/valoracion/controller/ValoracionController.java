package com.alertas.valoracion.controller;

import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.idempotencia.Idempotente;
import com.alertas.valoracion.dto.EstudianteValoracionResponse;
import com.alertas.valoracion.dto.RegistrarValoracionRequest;
import com.alertas.valoracion.dto.ValoracionResponse;
import com.alertas.valoracion.dto.ValoracionesConfigResponse;
import com.alertas.valoracion.service.ValoracionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// valoraciones de rutina del psicorientador. el admin las enciende en /api/v1/institucion/valoraciones
@RestController
@RequestMapping("/api/v1/valoraciones")
public class ValoracionController {

    private final ValoracionService valoracionService;

    public ValoracionController(ValoracionService valoracionService) {
        this.valoracionService = valoracionService;
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasAnyRole('PSICORIENTADOR', 'ADMIN')")
    public ValoracionesConfigResponse configuracion() {
        return valoracionService.configuracion();
    }

    @GetMapping("/estudiantes")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public PageResponse<EstudianteValoracionResponse> estudiantes(
            @RequestParam(defaultValue = "POR_VALORAR") String filtro,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return valoracionService.estudiantes(filtro, texto, gradoId, grupoId, pagina, tamanio);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    @Idempotente
    public ValoracionResponse registrar(@Valid @RequestBody RegistrarValoracionRequest request) {
        return valoracionService.registrar(request);
    }
}
