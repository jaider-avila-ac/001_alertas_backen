package com.alertas.miproceso.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

// lo que el estudiante ve de su propio proceso. nada de categoria, nivel, quien reporto ni observaciones
public record MiProcesoResponse(
        String psicorientador,
        List<Alerta> alertas,
        List<Solicitud> solicitudes,
        List<Cita> citas) {

    // una alerta que alguien reporto: solo el estado y la fecha
    public record Alerta(String estado, Instant fecha) {
    }

    // lo que el mismo escribio al pedir ayuda
    public record Solicitud(String estado, Instant fecha, String tema, String descripcion) {
    }

    public record Cita(
            String estado,
            OffsetDateTime inicio,
            OffsetDateTime fin,
            String modalidad,
            String lugar,
            String indicacion,
            String psicorientador) {
    }
}
