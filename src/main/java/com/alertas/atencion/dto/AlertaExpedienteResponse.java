package com.alertas.atencion.dto;

import com.alertas.alerta.repository.AlertaExpedienteFila;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AlertaExpedienteResponse(
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
        String gradoNombre,
        String grupoNombre,
        int anio,
        String reportadaPor,
        String psicorientador,
        String conclusion,
        Instant creadoEn,
        Instant completadaEn,
        List<SeguimientoResponse> seguimientos) {

    public static AlertaExpedienteResponse desde(AlertaExpedienteFila fila, List<SeguimientoResponse> seguimientos) {

        return new AlertaExpedienteResponse(
                fila.getCodigo(),
                fila.getOrigen(),
                fila.getEstado(),
                fila.getNivel(),
                Boolean.TRUE.equals(fila.getPrioritaria()),
                Boolean.TRUE.equals(fila.getPeligroInmediato()),
                fila.getCategoria(),
                fila.getDescripcion(),
                fila.getFechaHecho(),
                fila.getLugar(),
                fila.getHorarioSeguro(),
                fila.getModalidadPreferida(),
                fila.getAutorizaSmsFamiliares(),
                fila.getGradoNombre(),
                fila.getGrupoNombre(),
                fila.getAnio(),
                fila.getReportadaPor(),
                fila.getPsicorientador(),
                fila.getConclusion(),
                fila.getCreadoEn(),
                fila.getCompletadaEn(),
                seguimientos);
    }

    // para el admin: sin lo que escribio el psicorientador
    public AlertaExpedienteResponse sinConclusion() {

        return new AlertaExpedienteResponse(codigo, origen, estado, nivel, prioritaria, peligroInmediato, categoria,
                descripcion, fechaHecho, lugar, horarioSeguro, modalidadPreferida, autorizaSmsFamiliares, gradoNombre,
                grupoNombre, anio, reportadaPor, psicorientador, null, creadoEn, completadaEn, List.of());
    }
}
