package com.alertas.atencion.dto;

import com.alertas.estudiante.dto.EstudianteDetalleResponse;
import java.time.Instant;

// todo lo que va en el pdf de remision: datos del estudiante y su familia, y el seguimiento completo
public record RemisionResponse(
        String entidad,
        String motivo,
        String generadaPor,
        Instant generadaEn,
        EstudianteDetalleResponse estudiante,
        ExpedienteResponse expediente) {
}
