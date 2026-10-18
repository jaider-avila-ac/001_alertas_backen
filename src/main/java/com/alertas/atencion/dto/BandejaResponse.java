package com.alertas.atencion.dto;

import java.time.Instant;

// un estudiante en la bandeja. nivelMaximo: LEVE, MODERADO, ALTO o CRITICO
public record BandejaResponse(
        String codigo,
        String nombres,
        String apellidos,
        String grupo,
        long alertas,
        String nivelMaximo,
        boolean prioritaria,
        boolean pidioAyuda,
        boolean veniaEnAtencion,
        Instant desde) {
}
