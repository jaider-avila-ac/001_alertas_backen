package com.alertas.estudiante.dto;

import java.util.List;

// token es null cuando hay errores: primero se corrige el archivo y se vuelve a subir
public record VistaPreviaImportacionResponse(
        String token,
        int anio,
        int totalFilas,
        int nuevos,
        int actualizados,
        List<String> gruposACrear,
        List<ErrorFila> errores,
        int totalErrores) {
}
