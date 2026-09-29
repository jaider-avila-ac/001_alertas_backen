package com.alertas.atencion.dto;

import java.time.Instant;

// un estudiante en la bandeja. nivelMaximo: LEVE, MODERADO, ALTO o CRITICO
public record BandejaResponse(
        String codigo,
        String nombres,
        String apellidos,
        String gradoNombre,
        String grupoNombre,
        long alertas,
        String nivelMaximo,
        boolean prioritaria,
        boolean pidioAyuda,
        boolean veniaEnAtencion,
        Instant desde) {
}
