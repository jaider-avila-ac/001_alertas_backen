package com.alertas.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginSuperadminRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 60, message = "El usuario es muy largo")
        String usuario,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(max = 72, message = "La contrasena es muy larga")
        String contrasena) {
}
