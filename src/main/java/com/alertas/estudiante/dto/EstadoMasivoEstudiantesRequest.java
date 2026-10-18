package com.alertas.estudiante.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// se usa una sola forma: los seleccionados (codigos), un grupo, un rango de edad o todos
public record EstadoMasivoEstudiantesRequest(
        @Size(max = 2000, message = "Maximo 2000 estudiantes por vez")
        List<String> codigos,
        @Size(max = 60, message = "El grupo es muy largo")
        String grupo,
        String edad,
        boolean todos,
        @NotNull(message = "Falta indicar si quedan activos o no")
        Boolean activo) {
}
