package com.alertas.estudiante.dto;

import com.alertas.estudiante.model.Familiar;

public record FamiliarResponse(
        int posicion,
        String nombres,
        String apellidos,
        String parentesco,
        String celular,
        boolean recibeSms) {

    public static FamiliarResponse desde(Familiar familiar) {

        return new FamiliarResponse(
                familiar.getPosicion(),
                familiar.getNombres(),
                familiar.getApellidos(),
                familiar.getParentesco(),
                familiar.getCelular(),
                familiar.isRecibeSms());
    }
}
