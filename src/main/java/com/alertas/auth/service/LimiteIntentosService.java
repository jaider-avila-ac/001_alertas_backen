package com.alertas.auth.service;

// bloquea el login un rato despues de varios intentos fallidos, por usuario y por ip
public interface LimiteIntentosService {

    boolean estaBloqueado(String usuario, String ip);

    void registrarFallo(String usuario, String ip);

    void limpiar(String usuario);

    long getBloqueoMinutos();
}
