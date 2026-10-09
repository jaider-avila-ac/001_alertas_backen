package com.alertas.estructura.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CrearAnioRequest(

        @NotNull(message = "El año es obligatorio")
        @Min(value = 2000, message = "El año no es valido")
        @Max(value = 2100, message = "El año no es valido")
        Integer anio) {
}
