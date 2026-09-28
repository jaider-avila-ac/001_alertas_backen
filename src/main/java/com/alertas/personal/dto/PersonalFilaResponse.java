package com.alertas.personal.dto;

import com.alertas.personal.repository.PersonalFila;

public record PersonalFilaResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String correo,
        String rol,
        boolean activo) {

    public static PersonalFilaResponse desde(PersonalFila fila) {

        return new PersonalFilaResponse(
                fila.getCodigo(),
                fila.getTipoDoc(),
                fila.getNroDoc(),
                fila.getNombres(),
                fila.getApellidos(),
                fila.getCorreo(),
                fila.getRol(),
                Boolean.TRUE.equals(fila.getActivo()));
    }
}
