package com.alertas.atencion.dto;

import java.time.Instant;

// lo que se escribio de una alerta en una cita
public record SeguimientoResponse(Instant fecha, String observacion, String resultado) {
}
