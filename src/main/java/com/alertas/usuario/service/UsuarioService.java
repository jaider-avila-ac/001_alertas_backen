package com.alertas.usuario.service;

import com.alertas.auth.model.Rol;
import com.alertas.usuario.model.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

// todos trabajan sobre la institucion del contexto (requireTenant)
public interface UsuarioService {

    // usuario y contrasena inicial = numero de documento
    Usuario crear(String documento, Rol rol);

    boolean existeDocumento(String documento);

    Usuario buscarPorId(Long usuarioId);

    // null si no existe
    Usuario buscarPorDocumento(String documento);

    void registrarIngreso(Long usuarioId);

    // cuando se corrige el documento de la persona, el usuario cambia con el
    void cambiarDocumento(Long usuarioId, String documento);

    // de los documentos dados, los que ya tienen usuario en la institucion
    Set<String> documentosEnUso(Collection<String> documentos);

    // para la importacion por excel: cifra las contrasenas en paralelo (bcrypt es lento a proposito)
    Map<String, Usuario> crearVarios(List<String> documentos, Rol rol);

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
