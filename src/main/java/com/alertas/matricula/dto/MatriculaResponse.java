package com.alertas.matricula.dto;

import java.time.LocalDate;
import java.util.List;

// grupoId y anioId son de catalogo (no identifican a una persona), el front los usa en los selectores.
// editable: el anio es el activo o uno que viene, se puede cambiar de grupo o retirar
public record MatriculaResponse(
        Long anioId,
        int anio,
        boolean anioActivo,
        Long grupoId,
        String gradoNombre,
        String grupoNombre,
        String estado,
        String origen,
        LocalDate fechaMatricula,
        LocalDate fechaCierre,
        String motivoCierre,
        boolean editable,
        List<MovimientoResponse> movimientos) {
}
