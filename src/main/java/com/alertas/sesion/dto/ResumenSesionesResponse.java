package com.alertas.sesion.dto;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.SesionAbierta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// conteos de toda la institucion.
// actividadEnLinea: ultima actividad (ms) de las sesiones en linea, de menor a mayor. con eso el panel
// sabe cuando cada una pasa a inactiva sin volver a preguntar. ahora: hora del servidor (ms) para
// corregir si el reloj del equipo esta corrido
public record ResumenSesionesResponse(
        int sesiones,
        int usuarios,
        int enLinea,
        int administradores,
        int docentes,
        int psicorientadores,
        int estudiantes,
        List<Long> actividadEnLinea,
        long ahora) {

    public static ResumenSesionesResponse desde(List<SesionAbierta> abiertas, Instant limite) {

        Set<Long> usuarios = new HashSet<>();
        List<Long> actividad = new ArrayList<>();
        int administradores = 0;
        int docentes = 0;
        int psicorientadores = 0;
        int estudiantes = 0;

        for (SesionAbierta sesion : abiertas) {
            usuarios.add(sesion.usuarioId());

            if (sesion.ultimaActividad().isAfter(limite)) {
                actividad.add(sesion.ultimaActividad().toEpochMilli());
            }

            if (sesion.rol() == Rol.ADMIN) {
                administradores++;
            } else if (sesion.rol() == Rol.DOCENTE) {
                docentes++;
            } else if (sesion.rol() == Rol.PSICORIENTADOR) {
                psicorientadores++;
            } else if (sesion.rol() == Rol.ESTUDIANTE) {
                estudiantes++;
            }
        }

        Collections.sort(actividad);

        return new ResumenSesionesResponse(
                abiertas.size(),
                usuarios.size(),
                actividad.size(),
                administradores,
                docentes,
                psicorientadores,
                estudiantes,
                actividad,
                Instant.now().toEpochMilli());
    }
}
