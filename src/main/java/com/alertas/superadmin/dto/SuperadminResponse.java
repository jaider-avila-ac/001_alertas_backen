package com.alertas.superadmin.dto;

import com.alertas.superadmin.model.Superadministrador;

// sin id, como el resto de la api
public record SuperadminResponse(String usuario, String nombres) {

    public static SuperadminResponse desde(Superadministrador superadmin) {
        return new SuperadminResponse(superadmin.getUsuario(), superadmin.getNombres());
    }
}
