package com.alertas.notificacion.config;

import com.alertas.auth.service.SesionService;
import com.alertas.notificacion.service.EnVivoService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// la conexion de cada navegador. el servidor solo envia; lo que manda el navegador (un "ping")
// sirve para saber que la sesion sigue en linea
@Component
public class NotificacionesSocket extends TextWebSocketHandler {

    // la actividad se anota como mucho una vez por minuto
    private static final long TOQUE_MS = 60_000;

    private final EnVivoService enVivoService;
    private final SesionService sesionService;

    public NotificacionesSocket(EnVivoService enVivoService, SesionService sesionService) {

        this.enVivoService = enVivoService;
        this.sesionService = sesionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {

        Long institucionId = (Long) sesion.getAttributes().get("institucionId");
        Long usuarioId = (Long) sesion.getAttributes().get("usuarioId");
        enVivoService.conectar(sesion, institucionId, usuarioId);
        anotarActividad(sesion);
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
        // el navegador manda un ping cada tanto para que los proxys no cierren la conexion
        anotarActividad(sesion);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        enVivoService.desconectar(sesion);
    }

    private void anotarActividad(WebSocketSession sesion) {

        Object sesionId = sesion.getAttributes().get("sesionId");

        if (sesionId == null) {
            return;
        }

        long ahora = System.currentTimeMillis();
        Object anterior = sesion.getAttributes().get("ultimoToque");

        if (anterior != null && ahora - (Long) anterior < TOQUE_MS) {
            return;
        }

        sesion.getAttributes().put("ultimoToque", ahora);
        sesionService.registrarActividad((String) sesionId);
    }
}
