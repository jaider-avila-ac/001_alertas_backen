package com.alertas.estadistica.controller;

import com.alertas.estadistica.dto.EstadisticasResponse;
import com.alertas.estadistica.dto.FiltroEstadistica;
import com.alertas.estadistica.service.EstadisticaService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// estadisticas del colegio: el admin y el psicorientador (solo lectura). solo numeros, sin nombres de estudiantes.
// una sola peticion trae los conteos y las listas de los filtros. sin anioId se usa el anio activo; todos=true, todos los anios
@RestController
@RequestMapping("/api/v1/estadisticas")
@PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR')")
public class EstadisticaController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EstadisticaService estadisticaService;

    public EstadisticaController(EstadisticaService estadisticaService) {
        this.estadisticaService = estadisticaService;
    }

    @GetMapping
    public EstadisticasResponse resumen(
            @RequestParam(required = false) Long anioId,
            @RequestParam(defaultValue = "false") boolean todos,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long categoriaId) {

        return estadisticaService.resumen(new FiltroEstadistica(anioId, todos, desde, hasta, gradoId, grupoId, categoriaId));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) Long anioId,
            @RequestParam(defaultValue = "false") boolean todos,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long categoriaId) {

        byte[] contenido = estadisticaService.excel(
                new FiltroEstadistica(anioId, todos, desde, hasta, gradoId, grupoId, categoriaId));

        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"estadisticas.xlsx\"")
                .body(contenido);
    }
}
