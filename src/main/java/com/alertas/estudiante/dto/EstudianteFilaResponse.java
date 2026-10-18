package com.alertas.estudiante.dto;

import com.alertas.estudiante.repository.EstudianteFila;
import java.time.LocalDate;

// fila del listado. grupo es texto libre y puede venir null
public record EstudianteFilaResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        boolean activo,
        String grupo,
        LocalDate fechaNacimiento) {

    public static EstudianteFilaResponse desde(EstudianteFila fila) {

        return new EstudianteFilaResponse(
                fila.getCodigo(),
                fila.getTipoDoc(),
                fila.getNroDoc(),
                fila.getNombres(),
                fila.getApellidos(),
                Boolean.TRUE.equals(fila.getActivo()),
                fila.getGrupo(),
                fila.getFechaNacimiento());
    }
}
