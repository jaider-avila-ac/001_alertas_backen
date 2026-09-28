package com.alertas.estudiante.dto;

import com.alertas.estudiante.repository.EstudianteFila;

// fila del listado. grado y grupo son los del anio activo (null si no esta ubicado)
public record EstudianteFilaResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        boolean activo,
        String gradoNombre,
        String grupoNombre) {

    public static EstudianteFilaResponse desde(EstudianteFila fila) {

        return new EstudianteFilaResponse(
                fila.getCodigo(),
                fila.getTipoDoc(),
                fila.getNroDoc(),
                fila.getNombres(),
                fila.getApellidos(),
                Boolean.TRUE.equals(fila.getActivo()),
                fila.getGradoNombre(),
                fila.getGrupoNombre());
    }
}
