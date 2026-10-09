package com.alertas.inicio.service;

import com.alertas.inicio.dto.InicioResponse;

public interface InicioService {

    // lo que ve en inicio el usuario de la sesion, segun su rol
    InicioResponse dashboard();
}
