package com.alertas.shared.dto;

import jakarta.validation.constraints.NotNull;

// para prender o apagar algo: sms, acceso de estudiantes, un usuario
public record CambiarActivoRequest(

        @NotNull(message = "Falta indicar si queda activo o no")
        Boolean activo) {
}
