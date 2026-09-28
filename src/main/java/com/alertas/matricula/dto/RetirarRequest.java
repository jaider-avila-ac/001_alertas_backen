package com.alertas.matricula.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetirarRequest(

        @NotBlank(message = "Escribe el motivo del retiro")
        @Size(max = 300, message = "El motivo es muy largo")
        String motivo) {
}
