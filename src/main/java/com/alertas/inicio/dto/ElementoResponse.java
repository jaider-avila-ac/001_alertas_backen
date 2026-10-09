package com.alertas.inicio.dto;

import java.time.Instant;

// una fila de la lista del dashboard (una alerta o una cita). estado y nivel pueden venir null.
// grupo (el del momento de la alerta) y peligroInmediato solo vienen en las alertas
public record ElementoResponse(
        String titulo,
        String detalle,
        Instant fecha,
        String estado,
        String nivel,
        String enlace,
        String grupo,
        boolean peligroInmediato) {
}
