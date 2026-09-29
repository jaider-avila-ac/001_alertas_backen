package com.alertas.alerta.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// conclusion solo llega al psicorientador y al admin; al docente y al estudiante va null
public record AlertaDetalleResponse(
        String codigo,
        String origen,
        String estado,
        String nivel,
        boolean prioritaria,
        boolean peligroInmediato,
        String categoria,
        String descripcion,
        LocalDate fechaHecho,
        String lugar,
        String horarioSeguro,
        String modalidadPreferida,
        Boolean autorizaSmsFamiliares,
        String estudianteCodigo,
        String estudianteNombres,
        String estudianteApellidos,
        String gradoNombre,
        String grupoNombre,
        int anio,
        String reportadaPor,
        String psicorientador,
        String conclusion,
        OffsetDateTime creadoEn) {
}
