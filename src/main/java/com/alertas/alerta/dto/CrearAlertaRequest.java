package com.alertas.alerta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// la crea un docente (o un psicorientador o el admin)
public record CrearAlertaRequest(

        @NotBlank(message = "Selecciona el estudiante")
        String estudianteCodigo,

        @NotNull(message = "Selecciona la categoria")
        Long categoriaId,

        @NotBlank(message = "Selecciona el nivel")
        @Pattern(regexp = "LEVE|MODERADO|ALTO|CRITICO", message = "El nivel no es valido")
        String nivel,

        @NotBlank(message = "Describe lo que paso")
        @Size(min = 10, max = 5000, message = "La descripcion debe tener entre 10 y 5000 caracteres")
        String descripcion,

        @PastOrPresent(message = "La fecha del hecho no puede ser futura")
        LocalDate fechaHecho,

        @Size(max = 150, message = "El lugar es muy largo")
        String lugar,

        boolean peligroInmediato) {
}
