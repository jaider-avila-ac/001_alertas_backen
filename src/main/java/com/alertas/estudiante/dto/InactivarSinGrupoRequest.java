package com.alertas.estudiante.dto;

import jakarta.validation.constraints.NotNull;

public record InactivarSinGrupoRequest(

        @NotNull(message = "Falta el anio lectivo")
        Long anioId) {
}
