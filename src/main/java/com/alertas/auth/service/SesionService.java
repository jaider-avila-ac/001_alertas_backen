package com.alertas.auth.service;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;

// para sacar a alguien antes de que se venza su token: al inactivarlo o cambiarle la contrasena
public interface SesionService {

    void cerrarSesiones(Long institucionId, Long usuarioId);

    // todos los de un rol en una institucion (ej. inactivar a todos los estudiantes).
    // es una sola clave en redis, no una por usuario
    void cerrarSesionesDelRol(Long institucionId, Rol rol);

    boolean sigueVigente(UsuarioAutenticado usuario);
}
