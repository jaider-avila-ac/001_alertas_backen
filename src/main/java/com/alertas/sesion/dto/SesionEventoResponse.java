package com.alertas.sesion.dto;

// lo que le llega al panel por websocket cuando cambia una sesion.
// tipo: abierta (trae la sesion), actividad (la sesion con su nueva hora), cerrada (codigo y rol),
// rol (se cerraron todas las de ese rol) o todas
public record SesionEventoResponse(
        String tipo,
        String slug,
        String codigo,
        String rol,
        SesionActivaResponse sesion,
        ResumenSesionesResponse resumen) {
}
