package com.alertas.superadmin.service;

import com.alertas.superadmin.dto.CambiarContrasenaRequest;
import com.alertas.superadmin.dto.LoginSuperadminRequest;
import com.alertas.superadmin.dto.LoginSuperadminResponse;
import com.alertas.superadmin.dto.SuperadminResponse;

public interface SuperadminService {

    LoginSuperadminResponse login(LoginSuperadminRequest request, String ip);

    SuperadminResponse buscar(Long id);

    // devuelve un token nuevo porque los anteriores quedan cerrados
    LoginSuperadminResponse cambiarContrasena(Long id, CambiarContrasenaRequest request);

    // solo si todavia no hay ningun superadmin. se llama al arrancar
    void crearInicialSiNoExiste(String usuario, String contrasena, String nombres);
}
