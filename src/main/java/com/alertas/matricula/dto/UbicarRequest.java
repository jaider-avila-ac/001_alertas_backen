package com.alertas.matricula.dto;

import com.alertas.shared.validacion.ConLetras;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// matricular o mover de grupo. el anio es el del grupo
public record UbicarRequest(

        @NotNull(message = "Selecciona el grupo")
        Long grupoId,

        @Size(max = 300, message = "El motivo es muy largo")
        @ConLetras(message = "El motivo debe tener letras")
        String motivo) {
}
