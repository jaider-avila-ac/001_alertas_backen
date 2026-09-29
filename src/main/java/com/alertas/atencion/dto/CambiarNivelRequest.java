package com.alertas.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarNivelRequest(

        @NotBlank(message = "Selecciona el nivel")
        @Pattern(regexp = "LEVE|MODERADO|ALTO|CRITICO", message = "El nivel no es valido")
        String nivel) {
}
