package com.alertas.personal.dto;

import com.alertas.shared.dto.ErrorFila;
import java.util.List;

// token es null cuando hay errores: primero se corrige el archivo y se vuelve a subir
public record VistaPreviaPersonalResponse(
        String token,
        int totalFilas,
        int nuevos,
        int actualizados,
        List<ErrorFila> errores,
        int totalErrores) {
}
