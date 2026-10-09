package com.alertas.auth.service;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import java.time.Duration;

public interface JwtService {

    String generar(Long usuarioId, Long institucionId, String slug, Rol rol);

    String generar(Long usuarioId, Long institucionId, String slug, Rol rol, boolean debeCambiarContrasena);

    // sesionId: la sesion que abrio el login (ver SesionService.abrir)
    String generar(Long usuarioId, Long institucionId, String slug, Rol rol, boolean debeCambiarContrasena, String sesionId);

    // null si el token esta vencido, mal firmado o trae datos raros
    UsuarioAutenticado leer(String token);

    Duration getDuracion();
}
