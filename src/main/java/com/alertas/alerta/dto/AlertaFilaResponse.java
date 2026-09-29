package com.alertas.alerta.dto;

import com.alertas.alerta.repository.AlertaFila;
import java.time.Instant;
import java.time.LocalDate;

// fila de "mis alertas reportadas": sin observaciones del psicorientador
public record AlertaFilaResponse(
        String codigo,
        String origen,
        String estudianteCodigo,
        String estudianteNombres,
        String estudianteApellidos,
        String gradoNombre,
        String grupoNombre,
        int anio,
        String categoria,
        String nivel,
        String estado,
        boolean peligroInmediato,
        boolean prioritaria,
        LocalDate fechaHecho,
        Instant creadoEn) {

    public static AlertaFilaResponse desde(AlertaFila fila) {

        return new AlertaFilaResponse(
                fila.getCodigo(),
                fila.getOrigen(),
                fila.getEstudianteCodigo(),
                fila.getEstudianteNombres(),
                fila.getEstudianteApellidos(),
                fila.getGradoNombre(),
                fila.getGrupoNombre(),
                fila.getAnio(),
                fila.getCategoria(),
                fila.getNivel(),
                fila.getEstado(),
                Boolean.TRUE.equals(fila.getPeligroInmediato()),
                Boolean.TRUE.equals(fila.getPrioritaria()),
                fila.getFechaHecho(),
                fila.getCreadoEn());
    }
}
