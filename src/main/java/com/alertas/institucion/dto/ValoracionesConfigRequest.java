package com.alertas.institucion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ValoracionesConfigRequest(

        @NotNull(message = "Falta indicar si quedan activas o no")
        Boolean activas,

        @NotNull(message = "Falta indicar cada cuantos dias")
        @Min(value = 1, message = "Minimo 1 dia")
        @Max(value = 730, message = "Maximo 730 dias (dos anios)")
        Integer dias) {
}
