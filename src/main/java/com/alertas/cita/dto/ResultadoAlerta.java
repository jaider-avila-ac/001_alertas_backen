package com.alertas.cita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// lo que se hablo de una alerta en la cita y como queda. si queda completada, la observacion es su conclusion
public record ResultadoAlerta(

        @NotBlank(message = "Falta la alerta")
        String alertaCodigo,

        @NotBlank(message = "Elige si la alerta sigue en proceso o quedo completada")
        @Pattern(regexp = "EN_PROCESO|COMPLETADA", message = "El resultado no es valido")
        String resultado,

        @NotBlank(message = "Escribe la observacion de cada alerta")
        @Size(max = 5000, message = "La observacion es muy larga")
        String observacion) {
}
