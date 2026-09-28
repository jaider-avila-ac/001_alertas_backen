package com.alertas.personal.service;

import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.shared.dto.NombrePersona;
import java.util.List;

// trabaja sobre la institucion del contexto (requireTenant)
public interface PersonalService {

    AdministradorResponse crearAdministrador(AdministradorRequest request);

    List<AdministradorResponse> listarAdministradores();

    // falla si no existe o no es administrador
    AdministradorResponse buscarAdministrador(String codigo);

    // id del usuario del administrador, para uso interno del backend (nunca va al front)
    Long usuarioIdDeAdministrador(String codigo);

    // nombres de la persona de ese usuario, null si el usuario no es del personal
    NombrePersona buscarNombre(Long usuarioId);
}
