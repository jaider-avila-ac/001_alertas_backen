package com.alertas.matricula.controller;

import com.alertas.matricula.dto.ConfirmarPromocionRequest;
import com.alertas.matricula.dto.PromocionResponse;
import com.alertas.matricula.dto.ResultadoPromocionResponse;
import com.alertas.matricula.service.PromocionService;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// paso de anio: del anio activo al siguiente. solo el admin
@RestController
@RequestMapping("/api/v1/promocion")
@PreAuthorize("hasRole('ADMIN')")
public class PromocionController {

    private final PromocionService promocionService;

    public PromocionController(PromocionService promocionService) {
        this.promocionService = promocionService;
    }

    @GetMapping
    public PromocionResponse vistaPrevia() {
        return promocionService.vistaPrevia();
    }

    @PostMapping("/preparar-grupos")
    public PromocionResponse prepararGrupos() {
        return promocionService.prepararGrupos();
    }

    @PostMapping("/confirmar")
    @Idempotente
    public ResultadoPromocionResponse confirmar(@Valid @RequestBody(required = false) ConfirmarPromocionRequest request) {
        return promocionService.confirmar(request);
    }
}
