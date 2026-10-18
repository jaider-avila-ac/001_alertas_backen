package com.alertas.cita.dto;

import com.alertas.shared.validacion.ConLetras;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarCitaRequest(

        @NotBlank(message = "Escribe el motivo")
        @Size(max = 300, message = "El motivo es muy largo")
        @ConLetras(message = "El motivo debe tener letras")
        String motivo) {
}
