package com.alertas.sesion.config;

import com.alertas.sesion.service.SesionesEnVivoService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// la conexion del panel del superadmin. el servidor solo envia; el "ping" del navegador se ignora
@Component
public class SesionesSocket extends TextWebSocketHandler {

    private final SesionesEnVivoService enVivoService;

    public SesionesSocket(SesionesEnVivoService enVivoService) {
        this.enVivoService = enVivoService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        enVivoService.conectar(sesion);
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
        // ping para que los proxys no cierren la conexion
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        enVivoService.desconectar(sesion);
    }
}
