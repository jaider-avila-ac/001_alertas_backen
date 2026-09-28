package com.alertas.estructura.dto;

import com.alertas.estructura.model.Grado;

public record GradoResponse(Long id, String nombre, int orden, boolean activo) {

    public static GradoResponse desde(Grado grado) {
        return new GradoResponse(grado.getId(), grado.getNombre(), grado.getOrden(), grado.isActivo());
    }
}
