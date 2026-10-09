package com.alertas.auth.model;

import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// lo que viene dentro del token. el superadmin no tiene institucion ni slug.
// debeCambiarContrasena: todavia usa la contrasena asignada. no bloquea nada, el front sugiere cambiarla.
// sesionId: codigo de la sesion abierta al entrar (null en el superadmin y en tokens viejos)
public record UsuarioAutenticado(
        Long id,
        Long institucionId,
        String slug,
        Rol rol,
        boolean debeCambiarContrasena,
        Instant emitidoEn,
        String sesionId) {

    public boolean esSuperadmin() {
        return rol == Rol.SUPERADMIN;
    }

    // null si la solicitud no trae un token valido
    public static UsuarioAutenticado actual() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            return null;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof UsuarioAutenticado) {
            return (UsuarioAutenticado) principal;
        }

        return null;
    }
}
