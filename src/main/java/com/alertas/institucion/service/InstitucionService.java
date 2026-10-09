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

    // ---- superadmin: la institucion va por su slug y los administradores por su codigo ----

    // crea la institucion con sus grados, anio lectivo, categorias y primer administrador
    CrearInstitucionResponse crear(CrearInstitucionRequest request);

    // texto y activa pueden venir null (sin filtro)
    PageResponse<InstitucionResponse> listar(String texto, Boolean activa, int pagina, int tamanio);

    InstitucionResponse buscar(String slug);

    InstitucionResponse actualizar(String slug, InstitucionDatosRequest request);

    InstitucionResponse inactivar(String slug, String motivo);

    InstitucionResponse activar(String slug);

    InstitucionResponse cambiarSms(String slug, boolean activo);

    List<AdministradorResponse> listarAdministradores(String slug);

    AdministradorResponse crearAdministrador(String slug, AdministradorRequest request);

    // nombres, documento, correo y celular del administrador
    AdministradorResponse actualizarAdministrador(String slug, String codigo, AdministradorRequest request);

    AdministradorResponse restablecerContrasenaAdministrador(String slug, String codigo);

    AdministradorResponse asignarContrasenaAdministrador(String slug, String codigo, String nueva);

    AdministradorResponse cambiarEstadoAdministrador(String slug, String codigo, boolean activo);

    // ---- usuarios de la institucion (tenant del contexto) ----

    // nombre y slug, para la pantalla de login y el encabezado
    InstitucionPublicaResponse buscarPublica(Long id);

    InstitucionResponse miInstitucion();

    boolean valoracionesActivas(Long id);

    int valoracionesDias(Long id);

    // el admin abre o cierra el acceso de todos los estudiantes (ej. vacaciones)
    InstitucionResponse cambiarAccesoEstudiantes(boolean activo);

    // el admin enciende o apaga las valoraciones de rutina y dice cada cuantos dias
    InstitucionResponse cambiarValoraciones(boolean activas, int dias);
}
