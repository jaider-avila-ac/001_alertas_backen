package com.alertas.estudiante.dto;

import com.alertas.shared.dto.ErrorFila;
import java.util.List;

// token es null cuando hay errores: primero se corrige el archivo y se vuelve a subir
public record VistaPreviaImportacionResponse(
        String token,
        int totalFilas,
        int nuevos,
        int actualizados,
        List<ErrorFila> errores,
        int totalErrores) {
}
