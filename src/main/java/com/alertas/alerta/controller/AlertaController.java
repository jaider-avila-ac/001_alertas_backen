package com.alertas.alerta.controller;

import com.alertas.alerta.dto.AlertaDetalleResponse;
import com.alertas.alerta.dto.AlertaFilaResponse;
import com.alertas.alerta.dto.CrearAlertaRequest;
import com.alertas.alerta.dto.SolicitudAyudaRequest;
import com.alertas.alerta.service.AlertaService;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alertas")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DOCENTE', 'PSICORIENTADOR', 'ADMIN')")
    @Idempotente
    public AlertaDetalleResponse crear(@Valid @RequestBody CrearAlertaRequest request) {
        return alertaService.crear(request);
    }

    // solicitud de ayuda: el estudiante crea una alerta sobre si mismo
    @PostMapping("/solicitud-ayuda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Idempotente
    public AlertaDetalleResponse solicitarAyuda(@Valid @RequestBody SolicitudAyudaRequest request) {
        return alertaService.solicitarAyuda(request);
    }

    // estado: PENDIENTE, EN_PROCESO, COMPLETADA o vacio para todas
    @GetMapping("/mias")
    public PageResponse<AlertaFilaResponse> misReportadas(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        String filtro = null;
        if ("PENDIENTE".equals(estado) || "EN_PROCESO".equals(estado) || "COMPLETADA".equals(estado)) {
            filtro = estado;
        }

        return alertaService.misReportadas(filtro, pagina, tamanio);
    }

    @GetMapping("/{codigo}")
    public AlertaDetalleResponse buscar(@PathVariable String codigo) {
        return alertaService.buscar(codigo);
    }
}
