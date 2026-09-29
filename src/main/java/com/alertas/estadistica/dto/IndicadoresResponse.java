package com.alertas.estadistica.dto;

// horasPrimeraCita: promedio desde que se crea la alerta hasta su primera cita (null si ninguna tiene cita)
public record IndicadoresResponse(
        long alertas,
        long pendientes,
        long enProceso,
        long completadas,
        long prioritarias,
        long estudiantes,
        long citasRealizadas,
        long citasNoAsistio,
        long citasCanceladas,
        long citasProgramadas,
        Double horasPrimeraCita,
        long alertasConCita,
        long valoraciones,
        long estudiantesValorados) {
}
