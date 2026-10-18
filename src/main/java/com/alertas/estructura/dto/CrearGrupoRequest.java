package com.alertas.estructura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearGrupoRequest(

        @NotNull(message = "Falta el año lectivo")
        Long anioId,

        @NotNull(message = "Falta el grado")
        Long gradoId,

        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 20, message = "El nombre del grupo es muy largo, maximo 20 caracteres")
        @Pattern(regexp = "\\s*[A-Za-z0-9]+([ -][A-Za-z0-9]+)*\\s*", message = "El grupo solo lleva letras y numeros, ej: A, 01 o 6A")
        String nombre) {
}
