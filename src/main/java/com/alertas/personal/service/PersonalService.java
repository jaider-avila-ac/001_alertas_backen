package com.alertas.personal.service;

import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.personal.dto.NombrePersona;
import java.util.List;

// trabaja sobre la institucion del contexto (requireTenant)
public interface PersonalService {

    AdministradorResponse crearAdministrador(AdministradorRequest request);

    List<AdministradorResponse> listarAdministradores();

    // falla si el usuario no existe o no es administrador
    AdministradorResponse buscarAdministrador(Long usuarioId);

    // nombres de la persona de ese usuario, null si el usuario no es del personal
    NombrePersona buscarNombre(Long usuarioId);
}
