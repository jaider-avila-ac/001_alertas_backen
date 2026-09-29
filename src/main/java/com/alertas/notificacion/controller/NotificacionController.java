package com.alertas.notificacion.controller;

import com.alertas.notificacion.dto.NoLeidasResponse;
import com.alertas.notificacion.dto.NotificacionResponse;
import com.alertas.notificacion.dto.TicketResponse;
import com.alertas.notificacion.service.NotificacionService;
import com.alertas.notificacion.service.TicketService;
import com.alertas.shared.dto.PageResponse;
import com.alertas.usuario.dto.EstadoMasivoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// las notificaciones del usuario de la sesion (cualquier rol del colegio)
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final TicketService ticketService;

    public NotificacionController(NotificacionService notificacionService, TicketService ticketService) {

        this.notificacionService = notificacionService;
        this.ticketService = ticketService;
    }

    // ticket de un solo uso para abrir el websocket /ws/notificaciones?ticket=...
    @PostMapping("/ticket")
    public TicketResponse ticket() {
        return new TicketResponse(ticketService.crear());
    }

    @GetMapping
    public PageResponse<NotificacionResponse> mias(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return notificacionService.mias(pagina, tamanio);
    }

    // el contador de la campana al abrir la app o al reconectar; lo demas llega por websocket
    @GetMapping("/no-leidas")
    public NoLeidasResponse noLeidas() {
        return new NoLeidasResponse(notificacionService.noLeidas());
    }

    @PatchMapping("/{id}/leida")
    public NotificacionResponse marcarLeida(@PathVariable Long id) {
        return notificacionService.marcarLeida(id);
    }

    @PatchMapping("/leidas")
    public EstadoMasivoResponse marcarTodasLeidas() {
        return new EstadoMasivoResponse(notificacionService.marcarTodasLeidas());
    }
}
