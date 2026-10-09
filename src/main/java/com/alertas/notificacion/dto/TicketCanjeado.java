package com.alertas.notificacion.dto;

// de quien es la conexion. sesionId es null en tokens viejos sin sesion registrada
public record TicketCanjeado(long institucionId, long usuarioId, String sesionId) {
}
