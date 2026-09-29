package com.alertas.notificacion.service;

// el navegador no puede mandar el token en un websocket y en la url quedaria en los logs.
// por eso primero pide un ticket (con su token) y con el abre la conexion. sirve una sola vez y vence rapido
public interface TicketService {

    // ticket para el usuario de la sesion
    String crear();

    // [institucion, usuario] del ticket, null si no existe o ya se uso
    long[] canjear(String ticket);
}
