package com.alertas.cita.dto;

import java.time.OffsetDateTime;
import java.util.List;

// enCurso: el psicorientador la inicio y todavia no la finaliza
public record CitaResponse(
        String codigo,
        String estado,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String modalidad,
        String lugar,
        String indicacion,
        String motivoCancelacion,
        OffsetDateTime cerradaEn,
        String estudianteCodigo,
        String estudianteNombres,
        String estudianteApellidos,
        String gradoNombre,
        String grupoNombre,
        String psicorientador,
        OffsetDateTime iniciadaEn,
        boolean enCurso,
        List<AlertaDeCita> alertas) {
}
