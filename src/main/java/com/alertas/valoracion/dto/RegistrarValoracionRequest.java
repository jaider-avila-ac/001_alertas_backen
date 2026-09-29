package com.alertas.valoracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarValoracionRequest(

        @NotBlank(message = "Falta el estudiante")
        String estudianteCodigo,

        @NotBlank(message = "Escribe la observacion")
        @Size(min = 5, max = 5000, message = "La observacion debe tener entre 5 y 5000 caracteres")
        String observacion) {
}
