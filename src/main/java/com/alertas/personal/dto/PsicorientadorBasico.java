package com.alertas.personal.dto;

// uso interno (atencion y citas): el id no sale del backend
public record PsicorientadorBasico(Long id, String codigo, String nombres, String apellidos, Long usuarioId) {

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
