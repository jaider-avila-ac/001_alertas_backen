package com.alertas.sesion.service;

import org.springframework.web.socket.WebSocketSession;

// el panel del superadmin escucha por websocket los cambios de sesiones (alguien entro, salio o
// tuvo actividad) y vuelve a pedir la lista sin que nadie pulse nada
public interface SesionesEnVivoService {

    // ticket de un solo uso para abrir la conexion (el token no va en la url)
    String crearTicket();

    // id del superadmin, null si el ticket no existe o ya se uso
    Long canjearTicket(String ticket);

    void conectar(WebSocketSession sesion);

    void desconectar(WebSocketSession sesion);

    // lo llama redis con cada cambio: "institucion|tipo|codigo|rol"
    void alRecibir(String texto);
}
