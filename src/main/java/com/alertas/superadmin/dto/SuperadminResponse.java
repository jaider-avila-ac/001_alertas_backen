package com.alertas.superadmin.dto;

import com.alertas.superadmin.model.Superadministrador;

public record SuperadminResponse(Long id, String usuario, String nombres) {

    public static SuperadminResponse desde(Superadministrador superadmin) {
        return new SuperadminResponse(superadmin.getId(), superadmin.getUsuario(), superadmin.getNombres());
    }
}
