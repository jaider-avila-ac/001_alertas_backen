package com.alertas.alerta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// la crea el estudiante para si mismo
public record SolicitudAyudaRequest(

        @NotNull(message = "Selecciona el tema")
        Long categoriaId,

        @NotBlank(message = "Selecciona que tan urgente es")
        @Pattern(regexp = "BAJA|MEDIA|ALTA", message = "La urgencia no es valida")
        String urgencia,

        @NotBlank(message = "Cuentanos que pasa")
        @Size(min = 10, max = 5000, message = "Escribe entre 10 y 5000 caracteres")
        String descripcion,

        @Size(max = 150, message = "El horario es muy largo")
        String horarioSeguro,

        @Pattern(regexp = "PRESENCIAL|VIRTUAL", message = "La modalidad no es valida")
        String modalidadPreferida,

        boolean autorizaSmsFamiliares) {
}
