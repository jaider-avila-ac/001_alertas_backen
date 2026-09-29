package com.alertas.personal.service;

import com.alertas.personal.dto.ActualizarPersonalRequest;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.personal.dto.EstadoMasivoPersonalRequest;
import com.alertas.personal.dto.PersonalDetalleResponse;
import com.alertas.personal.dto.PersonalFilaResponse;
import com.alertas.personal.dto.PersonalRequest;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import java.util.List;

// trabaja sobre la institucion del contexto (requireTenant). afuera siempre por codigo
public interface PersonalService {

    // ---- administradores (los usa el superadmin) ----

    AdministradorResponse crearAdministrador(AdministradorRequest request);

    List<AdministradorResponse> listarAdministradores();

    // falla si no existe o no es administrador
    AdministradorResponse buscarAdministrador(String codigo);

    // id del usuario del administrador, para uso interno del backend (nunca va al front)
    Long usuarioIdDeAdministrador(String codigo);

    // ---- docentes y psicorientadores (los maneja el admin del colegio) ----

    // rol y activo pueden venir null (sin filtro)
    PageResponse<PersonalFilaResponse> listar(String texto, String rol, Boolean activo, int pagina, int tamanio);

    PersonalDetalleResponse buscar(String codigo);

    PersonalDetalleResponse crear(PersonalRequest request);

    PersonalDetalleResponse actualizar(String codigo, ActualizarPersonalRequest request);

    PersonalDetalleResponse restablecerContrasena(String codigo);

    PersonalDetalleResponse cambiarEstado(String codigo, boolean activo);

    int cambiarEstadoMasivo(EstadoMasivoPersonalRequest request);

    // ---- para otros modulos ----

    // nombres de la persona de ese usuario, null si el usuario no es del personal
    NombrePersona buscarNombre(Long usuarioId);

    // null si ese usuario no es psicorientador
    PsicorientadorBasico psicorientadorDeUsuario(Long usuarioId);

    // falla con 404 si no es un psicorientador activo de la institucion
    PsicorientadorBasico psicorientadorActivo(String codigo);

    // para elegir a quien reasignar. son pocos, no se pagina
    List<PsicorientadorBasico> psicorientadoresActivos();
}
