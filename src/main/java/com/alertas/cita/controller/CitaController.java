package com.alertas.cita.controller;

import com.alertas.cita.dto.AgendarCitaRequest;
import com.alertas.cita.dto.CancelarCitaRequest;
import com.alertas.cita.dto.CitaResponse;
import com.alertas.cita.dto.ReprogramarCitaRequest;
import com.alertas.cita.dto.ResultadoCitaRequest;
import com.alertas.cita.service.CitaService;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    @Idempotente
    public CitaResponse agendar(@Valid @RequestBody AgendarCitaRequest request) {
        return citaService.agendar(request);
    }

    // desde y hasta: yyyy-mm-dd
    @GetMapping("/agenda")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public List<CitaResponse> agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return citaService.agenda(desde, hasta);
    }

    @GetMapping("/{codigo}")
    @PreAuthorize("hasAnyRole('PSICORIENTADOR', 'ADMIN')")
    public CitaResponse buscar(@PathVariable String codigo) {
        return citaService.buscar(codigo);
    }

    @PostMapping("/{codigo}/iniciar")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public CitaResponse iniciar(@PathVariable String codigo) {
        return citaService.iniciar(codigo);
    }

    @PostMapping("/{codigo}/finalizar")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public CitaResponse finalizar(@PathVariable String codigo, @Valid @RequestBody ResultadoCitaRequest request) {
        return citaService.finalizar(codigo, request);
    }

    @PostMapping("/{codigo}/no-asistio")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public CitaResponse noAsistio(@PathVariable String codigo) {
        return citaService.noAsistio(codigo);
    }

    @PostMapping("/{codigo}/cancelar")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public CitaResponse cancelar(@PathVariable String codigo, @Valid @RequestBody CancelarCitaRequest request) {
        return citaService.cancelar(codigo, request);
    }

    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('PSICORIENTADOR')")
    public CitaResponse reprogramar(@PathVariable String codigo, @Valid @RequestBody ReprogramarCitaRequest request) {
        return citaService.reprogramar(codigo, request);
    }
}
