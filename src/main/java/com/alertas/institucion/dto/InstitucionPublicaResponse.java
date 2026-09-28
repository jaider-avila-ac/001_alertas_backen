package com.alertas.institucion.dto;

import com.alertas.institucion.model.Institucion;

// lo que puede ver cualquiera que abra el enlace del colegio
public record InstitucionPublicaResponse(String nombre, String slug) {

    public static InstitucionPublicaResponse desde(Institucion institucion) {
        return new InstitucionPublicaResponse(institucion.getNombre(), institucion.getSlug());
    }
}
