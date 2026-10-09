package com.alertas.estudiante.dto;

import jakarta.validation.constraints.NotNull;

public record InactivarSinGrupoRequest(

        @NotNull(message = "Falta el año lectivo")
        Long anioId) {
}
