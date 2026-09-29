package com.alertas.estadistica.controller;

import com.alertas.estadistica.dto.ComparativoResponse;
import com.alertas.estadistica.dto.EstadisticasGlobalesResponse;
import com.alertas.estadistica.service.EstadisticaGlobalService;
import com.alertas.shared.dto.PageResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// estadisticas globales del superadmin (SecurityConfig pide el rol en /api/v1/superadmin/**)
@RestController
@RequestMapping("/api/v1/superadmin/estadisticas")
public class EstadisticaGlobalController {

    private final EstadisticaGlobalService estadisticaGlobalService;

    public EstadisticaGlobalController(EstadisticaGlobalService estadisticaGlobalService) {
        this.estadisticaGlobalService = estadisticaGlobalService;
    }

    @GetMapping
    public EstadisticasGlobalesResponse resumen(
            @RequestParam(required = false) String institucion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return estadisticaGlobalService.resumen(institucion, desde, hasta);
    }

    @GetMapping("/instituciones")
    public PageResponse<ComparativoResponse> comparativo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return estadisticaGlobalService.comparativo(desde, hasta, pagina, tamanio);
    }
}
