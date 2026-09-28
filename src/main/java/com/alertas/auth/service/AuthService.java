package com.alertas.auth.service;

import com.alertas.auth.dto.CambiarContrasenaRequest;
import com.alertas.auth.dto.LoginRequest;
import com.alertas.auth.dto.LoginResponse;
import com.alertas.auth.dto.PerfilResponse;

// login de los usuarios de una institucion (el del superadmin va aparte)
public interface AuthService {

    // la institucion ya viene resuelta por el slug de la url (TenantInterceptor)
    LoginResponse login(LoginRequest request, String ip);

    PerfilResponse perfil();

    // devuelve un token nuevo, el anterior queda cerrado
    LoginResponse cambiarContrasena(CambiarContrasenaRequest request);
}
