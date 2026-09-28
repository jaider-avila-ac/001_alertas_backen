package com.alertas.estructura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearGrupoRequest(

        @NotNull(message = "Falta el anio lectivo")
        Long anioId,

        @NotNull(message = "Falta el grado")
        Long gradoId,

        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 20, message = "El nombre del grupo es muy largo, maximo 20 caracteres")
        String nombre) {
}
