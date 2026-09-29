package com.alertas.cita.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record ReprogramarCitaRequest(

        @NotNull(message = "Selecciona la fecha y la hora")
        @Future(message = "La cita debe ser en el futuro")
        OffsetDateTime inicio,

        @Min(value = 15, message = "La cita dura al menos 15 minutos")
        @Max(value = 240, message = "La cita dura maximo 4 horas")
        int duracionMinutos,

        @NotBlank(message = "Selecciona la modalidad")
        @Pattern(regexp = "PRESENCIAL|VIRTUAL", message = "La modalidad no es valida")
        String modalidad,

        @Size(max = 200, message = "El lugar es muy largo")
        String lugar,

        @Size(max = 500, message = "La indicacion es muy larga")
        String indicacion) {
}
