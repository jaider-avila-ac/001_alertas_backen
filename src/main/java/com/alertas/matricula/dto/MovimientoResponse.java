package com.alertas.matricula.dto;

import java.time.Instant;

public record MovimientoResponse(
        Instant fecha,
        String gradoAnterior,
        String grupoAnterior,
        String gradoNuevo,
        String grupoNuevo,
        String motivo) {
}
