package com.alertas.estudiante.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarGrupoRequest(

        @NotNull(message = "Falta el grupo")
        Long grupoId) {
}
