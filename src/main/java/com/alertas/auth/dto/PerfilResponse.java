package com.alertas.auth.dto;

import com.alertas.auth.model.Rol;
import com.alertas.institucion.dto.InstitucionPublicaResponse;

public record PerfilResponse(
        String usuario,
        String nombres,
        String apellidos,
        Rol rol,
        boolean debeCambiarContrasena,
        InstitucionPublicaResponse institucion) {
}
