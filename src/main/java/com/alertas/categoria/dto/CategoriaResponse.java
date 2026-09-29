package com.alertas.categoria.dto;

import com.alertas.categoria.model.CategoriaAlerta;

// el id es de catalogo, no identifica a una persona
public record CategoriaResponse(Long id, String nombre, boolean activa, long totalAlertas) {

    public static CategoriaResponse desde(CategoriaAlerta categoria, long totalAlertas) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.isActiva(), totalAlertas);
    }
}
