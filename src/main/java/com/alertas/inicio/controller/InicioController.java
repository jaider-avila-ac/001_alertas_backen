package com.alertas.inicio.controller;

import com.alertas.inicio.dto.InicioResponse;
import com.alertas.inicio.service.InicioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// dashboard de inicio: una sola peticion con todo lo que ve el usuario segun su rol
@RestController
public class InicioController {

    private final InicioService inicioService;

    public InicioController(InicioService inicioService) {
        this.inicioService = inicioService;
    }

    @GetMapping("/api/v1/inicio")
    public InicioResponse dashboard() {
        return inicioService.dashboard();
    }
}
