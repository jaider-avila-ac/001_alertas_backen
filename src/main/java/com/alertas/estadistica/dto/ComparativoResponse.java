package com.alertas.estadistica.dto;

// una fila del comparativo entre colegios. estudiantes: activos hoy (para comparar colegios de distinto tamanio)
public record ComparativoResponse(
        String nombre,
        String slug,
        boolean activa,
        long estudiantes,
        long alertas,
        long pendientes,
        long enProceso,
        long completadas,
        long smsEnviados,
        long smsSegmentos) {
}
