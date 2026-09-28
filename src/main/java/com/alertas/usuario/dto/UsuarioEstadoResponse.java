package com.alertas.usuario.dto;

import com.alertas.auth.model.Rol;
import com.alertas.usuario.model.Usuario;

public record UsuarioEstadoResponse(
        Long usuarioId,
        String usuario,
        Rol rol,
        boolean activo,
        boolean debeCambiarContrasena) {

    public static UsuarioEstadoResponse desde(Usuario usuario) {

        return new UsuarioEstadoResponse(
                usuario.getId(),
                usuario.getUsuario(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.isDebeCambiarContrasena());
    }
}
