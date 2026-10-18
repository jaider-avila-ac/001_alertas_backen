package com.alertas.estudiante.dto;

import com.alertas.shared.dto.ErrorFila;
import java.util.List;

// token es null cuando hay errores o no hay a quien inactivar.
// estudiantes: los primeros que se van a inactivar, el total va en seInactivan
public record VistaPreviaInactivacionResponse(
        String token,
        int totalFilas,
        int seInactivan,
        int yaInactivos,
        List<EstudianteAInactivar> estudiantes,
        List<ErrorFila> errores,
        int totalErrores) {
}
