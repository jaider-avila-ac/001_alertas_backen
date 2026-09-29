package com.alertas.valoracion.dto;

import com.alertas.valoracion.repository.ValoracionFila;
import java.time.Instant;

// observacion null cuando quien consulta no es psicorientador
public record ValoracionResponse(
        String codigo,
        Instant fecha,
        String psicorientador,
        String observacion,
        String gradoNombre,
        String grupoNombre,
        Integer anio) {

    public static ValoracionResponse desde(ValoracionFila fila, boolean conObservacion) {

        String observacion = null;
        if (conObservacion) {
            observacion = fila.getObservacion();
        }

        return new ValoracionResponse(
                fila.getCodigo(),
                fila.getFecha(),
                fila.getPsicorientador(),
                observacion,
                fila.getGradoNombre(),
                fila.getGrupoNombre(),
                fila.getAnio());
    }
}
