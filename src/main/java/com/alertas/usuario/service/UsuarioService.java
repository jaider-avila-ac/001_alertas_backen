package com.alertas.usuario.service;

import com.alertas.auth.model.Rol;
import com.alertas.usuario.model.Usuario;
import java.util.List;

// todos trabajan sobre la institucion del contexto (requireTenant)
public interface UsuarioService {

    // usuario y contrasena inicial = numero de documento
    Usuario crear(String documento, Rol rol);

    boolean existeDocumento(String documento);

    Usuario buscarPorId(Long usuarioId);

    // null si no existe
    Usuario buscarPorDocumento(String documento);

    void registrarIngreso(Long usuarioId);

    // ---- contrasenas ----

    // vuelve a dejar el documento como contrasena. superadmin sobre cualquier usuario
    void restablecerContrasena(Long usuarioId);

    // el superadmin le pone una contrasena al administrador (el admin no puede cambiar la suya)
    void asignarContrasena(Long usuarioId, String nueva);

    // la propia. el administrador no puede
    void cambiarContrasenaPropia(Long usuarioId, String actual, String nueva);

    // el admin sobre docentes, psicorientadores y estudiantes, nunca sobre otro administrador
    Usuario restablecerContrasenaPorAdmin(Long usuarioId);

    // ---- activar e inactivar ----

    // superadmin sobre cualquier usuario
    void cambiarEstado(Long usuarioId, boolean activo);

    Usuario cambiarEstadoPorAdmin(Long usuarioId, boolean activo);

    // si usuarioIds viene vacio se aplica a todos los del rol. devuelve cuantos cambiaron
    int cambiarEstadoMasivo(Rol rol, List<Long> usuarioIds, boolean activo);
}
