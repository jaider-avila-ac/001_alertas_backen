package com.alertas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(

        @NotBlank(message = "La contrasena actual es obligatoria")
        String actual,

        @NotBlank(message = "La contrasena nueva es obligatoria")
        @Size(min = 8, max = 72, message = "La contrasena nueva debe tener entre 8 y 72 caracteres")
        String nueva) {
}
