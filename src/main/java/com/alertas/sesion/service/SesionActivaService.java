package com.alertas.sesion.service;

import com.alertas.sesion.dto.CerrarSesionesResponse;
import com.alertas.sesion.dto.SesionesResponse;

// el superadmin ve quien tiene la sesion abierta en una institucion y la puede cerrar al instante
public interface SesionActivaService {

    // rol puede venir null (todas)
    SesionesResponse listar(String slug, String rol, int pagina, int tamanio);

    void cerrar(String slug, String codigo);

    CerrarSesionesResponse cerrarTodas(String slug);
}
