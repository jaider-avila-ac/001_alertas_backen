package com.alertas.estudiante.dto;

import java.time.LocalDate;
import java.util.List;

public record EstudianteDetalleResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String grupo,
        String genero,
        LocalDate fechaNacimiento,
        String celular,
        String correo,
        String direccion,
        String barrio,
        String eps,
        String rh,
        String condicionesSalud,
        boolean smsFamiliares,
        boolean activo,
        boolean debeCambiarContrasena,
        List<FamiliarResponse> familiares) {
}
