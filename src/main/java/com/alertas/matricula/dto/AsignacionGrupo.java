package com.alertas.matricula.dto;

import jakarta.validation.constraints.NotNull;

// grupoDestinoId null: ese grupo no pasa por ahora
public record AsignacionGrupo(

        @NotNull(message = "Falta el grupo de origen")
        Long grupoOrigenId,

        Long grupoDestinoId) {
}
