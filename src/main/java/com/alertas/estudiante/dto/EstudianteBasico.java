package com.alertas.estudiante.dto;

// uso interno de otros modulos (alertas, citas): el id no sale del backend
public record EstudianteBasico(Long id, String codigo, String nombres, String apellidos, String grupo, boolean activo, Long usuarioId) {

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
