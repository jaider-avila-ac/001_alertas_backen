package com.alertas.notificacion.config;

import com.alertas.notificacion.service.EnVivoService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// la conexion de cada navegador. el servidor solo envia; lo que manda el navegador (un "ping") se ignora
@Component
public class NotificacionesSocket extends TextWebSocketHandler {

    private final EnVivoService enVivoService;

    public NotificacionesSocket(EnVivoService enVivoService) {
        this.enVivoService = enVivoService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {

        Long institucionId = (Long) sesion.getAttributes().get("institucionId");
        Long usuarioId = (Long) sesion.getAttributes().get("usuarioId");
        enVivoService.conectar(sesion, institucionId, usuarioId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
        // el navegador manda un ping cada tanto para que los proxys no cierren la conexion
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        enVivoService.desconectar(sesion);
    }
}
