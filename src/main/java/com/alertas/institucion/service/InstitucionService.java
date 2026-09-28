package com.alertas.institucion.service;

import com.alertas.institucion.dto.CrearInstitucionRequest;
import com.alertas.institucion.dto.CrearInstitucionResponse;
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.dto.InstitucionDatosRequest;
import com.alertas.institucion.dto.InstitucionPublicaResponse;
import com.alertas.institucion.dto.InstitucionResponse;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.shared.dto.PageResponse;
import java.util.List;

public interface InstitucionService {

    // ---- lo que se revisa en cada solicitud (con cache) ----

    // null si no existe
    EstadoInstitucion estadoPorSlug(String slug);

    EstadoInstitucion estadoPorId(Long id);

    // ---- superadmin ----

    // crea la institucion con sus grados, anio lectivo, categorias y primer administrador
    CrearInstitucionResponse crear(CrearInstitucionRequest request);

    // texto y activa pueden venir null (sin filtro)
    PageResponse<InstitucionResponse> listar(String texto, Boolean activa, int pagina, int tamanio);

    InstitucionResponse buscar(Long id);

    InstitucionResponse actualizar(Long id, InstitucionDatosRequest request);

    InstitucionResponse inactivar(Long id, String motivo);

    InstitucionResponse activar(Long id);

    InstitucionResponse cambiarSms(Long id, boolean activo);

    List<AdministradorResponse> listarAdministradores(Long id);

    AdministradorResponse crearAdministrador(Long id, AdministradorRequest request);

    AdministradorResponse restablecerContrasenaAdministrador(Long id, Long usuarioId);

    AdministradorResponse asignarContrasenaAdministrador(Long id, Long usuarioId, String nueva);

    AdministradorResponse cambiarEstadoAdministrador(Long id, Long usuarioId, boolean activo);

    // ---- usuarios de la institucion (tenant del contexto) ----

    // nombre y slug, para la pantalla de login y el encabezado
    InstitucionPublicaResponse buscarPublica(Long id);

    InstitucionResponse miInstitucion();

    // el admin abre o cierra el acceso de todos los estudiantes (ej. vacaciones)
    InstitucionResponse cambiarAccesoEstudiantes(boolean activo);
}
