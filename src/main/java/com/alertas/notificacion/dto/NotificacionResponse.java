package com.alertas.notificacion.dto;

import com.alertas.notificacion.model.Notificacion;
import java.time.OffsetDateTime;

// el id es de una notificacion del mismo usuario, no identifica a una persona
public record NotificacionResponse(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        String enlace,
        boolean leida,
        OffsetDateTime creadoEn) {

    public static NotificacionResponse desde(Notificacion notificacion) {

        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTipo(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getEnlace(),
                notificacion.isLeida(),
                notificacion.getCreadoEn());
    }
}
