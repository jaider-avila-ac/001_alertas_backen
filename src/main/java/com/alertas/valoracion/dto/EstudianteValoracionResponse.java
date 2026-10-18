package com.alertas.valoracion.dto;

import java.time.Instant;

// ultima y ultimaPor vienen null si nunca lo han valorado
public record EstudianteValoracionResponse(
        String codigo,
        String nombres,
        String apellidos,
        String grupo,
        Instant ultima,
        String ultimaPor,
        boolean porValorar) {
}
