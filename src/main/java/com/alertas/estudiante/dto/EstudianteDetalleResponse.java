package com.alertas.estudiante.dto;

import java.time.LocalDate;
import java.util.List;

// ubicacionActual es null si no esta ubicado en el anio activo
public record EstudianteDetalleResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String genero,
        LocalDate fechaNacimiento,
        String celular,
        boolean smsFamiliares,
        boolean activo,
        boolean debeCambiarContrasena,
        UbicacionResponse ubicacionActual,
        List<UbicacionResponse> historial,
        List<FamiliarResponse> familiares) {
}
