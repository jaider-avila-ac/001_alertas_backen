package com.alertas.auth.service;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.SesionAbierta;
import com.alertas.auth.model.UsuarioAutenticado;
import java.time.Duration;
import java.util.List;

// sesiones abiertas (en redis) y cierre antes de que se venza el token:
// al inactivar, al cambiar la contrasena o cuando el superadmin las cierra
public interface SesionService {

    // canal de redis: cada vez que una sesion se abre, se cierra o registra actividad se publica
    // "institucion|tipo|codigo|rol" y el panel del superadmin actualiza solo esa fila
    String CANAL_CAMBIOS = "sesiones:cambios";

    // en linea: con actividad en este tiempo (el navegador hace ping cada 25 s y se anota cada minuto)
    Duration EN_LINEA = Duration.ofMinutes(2);

    // al entrar: guarda equipo, sistema, navegador y quien es. devuelve el codigo que va dentro del token
    String abrir(Long institucionId, Long usuarioId, Rol rol, String userAgent, String documento, String nombres, String apellidos);

    // null si no esta abierta o no es de esa institucion
    SesionAbierta buscar(Long institucionId, String sesionId);

    // la sesion sigue en uso (ping del websocket)
    void registrarActividad(String sesionId);

    // las abiertas de una institucion, la mas reciente primero
    List<SesionAbierta> listar(Long institucionId);

    // false si no existe o no es de esa institucion
    boolean cerrar(Long institucionId, String sesionId);

    // devuelve cuantas se cerraron
    int cerrarTodas(Long institucionId);

    void cerrarSesiones(Long institucionId, Long usuarioId);

    // todas las del usuario menos "excepto" (la que esta cambiando su contrasena)
    void cerrarSesiones(Long institucionId, Long usuarioId, String excepto);

    // todos los de un rol en una institucion (ej. inactivar a todos los estudiantes).
    // es una sola clave en redis, no una por usuario
    void cerrarSesionesDelRol(Long institucionId, Rol rol);

    // tambien deja anotada la actividad (como mucho una vez por minuto)
    boolean sigueVigente(UsuarioAutenticado usuario);
}
