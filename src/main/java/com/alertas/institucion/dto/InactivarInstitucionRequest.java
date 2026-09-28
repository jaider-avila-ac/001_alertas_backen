package com.alertas.institucion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InactivarInstitucionRequest(

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 300, message = "El motivo es muy largo, maximo 300 caracteres")
        String motivo) {
}
