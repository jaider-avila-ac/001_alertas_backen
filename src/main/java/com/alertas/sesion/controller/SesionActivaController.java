package com.alertas.sesion.controller;

import com.alertas.sesion.dto.CerrarSesionesResponse;
import com.alertas.sesion.dto.SesionesResponse;
import com.alertas.sesion.dto.TicketResponse;
import com.alertas.sesion.service.SesionActivaService;
import com.alertas.sesion.service.SesionesEnVivoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// sesiones abiertas de cada institucion (solo superadmin, ver SecurityConfig)
@RestController
@RequestMapping("/api/v1/superadmin")
public class SesionActivaController {

    private final SesionActivaService service;
    private final SesionesEnVivoService enVivoService;

    public SesionActivaController(SesionActivaService service, SesionesEnVivoService enVivoService) {

        this.service = service;
        this.enVivoService = enVivoService;
    }

    @GetMapping("/instituciones/{slug}/sesiones")
    public SesionesResponse listar(
            @PathVariable String slug,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return service.listar(slug, rol, pagina, tamanio);
    }

    @DeleteMapping("/instituciones/{slug}/sesiones/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrar(@PathVariable String slug, @PathVariable String codigo) {
        service.cerrar(slug, codigo);
    }

    @DeleteMapping("/instituciones/{slug}/sesiones")
    public CerrarSesionesResponse cerrarTodas(@PathVariable String slug) {
        return service.cerrarTodas(slug);
    }

    // para abrir /ws/superadmin y recibir los cambios al instante
    @PostMapping("/sesiones/ticket")
    public TicketResponse ticket() {
        return new TicketResponse(enVivoService.crearTicket());
    }
}
