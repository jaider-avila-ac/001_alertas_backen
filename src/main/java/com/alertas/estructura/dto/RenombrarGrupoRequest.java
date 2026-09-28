package com.alertas.estructura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenombrarGrupoRequest(

        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 20, message = "El nombre del grupo es muy largo, maximo 20 caracteres")
        String nombre) {
}
