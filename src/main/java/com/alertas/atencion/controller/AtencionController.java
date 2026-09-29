package com.alertas.atencion.controller;

import com.alertas.atencion.dto.BandejaResponse;
import com.alertas.atencion.dto.CambiarNivelRequest;
import com.alertas.atencion.dto.EstudianteAtencionResponse;
import com.alertas.atencion.dto.ExpedienteResponse;
import com.alertas.atencion.dto.PsicorientadorResponse;
import com.alertas.atencion.dto.ReasignarRequest;
import com.alertas.atencion.service.AtencionService;
import com.alertas.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// trabajo del psicorientador. el admin solo ve expedientes y reasigna
@RestController
@RequestMapping("/api/v1/atencion")
public class AtencionController {

    private final AtencionService atencionService;

    public AtencionController(AtencionService atencionService) {
        this.atencionService = atencionService;
    }

    @GetMapping("/bandeja")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public PageResponse<BandejaResponse> bandeja(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return atencionService.bandeja(pagina, tamanio);
    }

    @GetMapping("/mis-estudiantes")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public PageResponse<EstudianteAtencionResponse> misEstudiantes(
            @RequestParam(defaultValue = "POR_AGENDAR") String pestana,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        String filtroNivel = null;
        if ("LEVE".equals(nivel) || "MODERADO".equals(nivel) || "ALTO".equals(nivel) || "CRITICO".equals(nivel)) {
            filtroNivel = nivel;
        }

        return atencionService.misEstudiantes(pestana, filtroNivel, categoriaId, gradoId, grupoId, pagina, tamanio);
    }

    @GetMapping("/estudiantes/{codigo}")
    @PreAuthorize("hasAnyRole('PSICORIENTADOR', 'ADMIN')")
    public ExpedienteResponse expediente(@PathVariable String codigo) {
        return atencionService.expediente(codigo);
    }

    @PostMapping("/estudiantes/{codigo}/tomar")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public ExpedienteResponse tomar(@PathVariable String codigo) {
        return atencionService.tomar(codigo);
    }

    @PostMapping("/estudiantes/{codigo}/reasignar")
    @PreAuthorize("hasAnyRole('PSICORIENTADOR', 'ADMIN')")
    public ExpedienteResponse reasignar(@PathVariable String codigo, @Valid @RequestBody ReasignarRequest request) {
        return atencionService.reasignar(codigo, request);
    }

    @PatchMapping("/alertas/{codigo}/nivel")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public ExpedienteResponse cambiarNivel(@PathVariable String codigo, @Valid @RequestBody CambiarNivelRequest request) {
        return atencionService.cambiarNivel(codigo, request.nivel());
    }

    @PostMapping("/alertas/{codigo}/reabrir")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public ExpedienteResponse reabrir(@PathVariable String codigo) {
        return atencionService.reabrir(codigo);
    }

    @GetMapping("/psicorientadores")
    @PreAuthorize("hasAnyRole('PSICORIENTADOR', 'ADMIN')")
    public List<PsicorientadorResponse> psicorientadores() {
        return atencionService.psicorientadores();
    }
}
