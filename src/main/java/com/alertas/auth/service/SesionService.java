package com.alertas.auth.service;

import com.alertas.auth.model.UsuarioAutenticado;

// para sacar a alguien antes de que se venza su token: al inactivarlo o cambiarle la contrasena
public interface SesionService {

    void cerrarSesiones(Long institucionId, Long usuarioId);

    boolean sigueVigente(UsuarioAutenticado usuario);
}
