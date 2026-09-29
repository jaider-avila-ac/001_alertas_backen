package com.alertas.atencion.dto;

import java.time.Instant;

// fila de "mis estudiantes". nivelMaximo es null si ya no tiene alertas activas
public record EstudianteAtencionResponse(
        String codigo,
        String nombres,
        String apellidos,
        String gradoNombre,
        String grupoNombre,
        long activas,
        String nivelMaximo,
        boolean prioritaria,
        String citaCodigo,
        Instant citaInicio,
        Instant citaFin) {
}
