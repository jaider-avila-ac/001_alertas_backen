package com.alertas.superadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginSuperadminRequest(

        @NotBlank(message = "El usuario es obligatorio")
        String usuario,

        @NotBlank(message = "La contrasena es obligatoria")
        String contrasena) {
}
