package com.alertas.estudiante.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// se reemplazan todos los familiares de una vez (maximo 3)
public record FamiliaresRequest(

        @NotNull(message = "Falta la lista de familiares")
        @Size(max = 3, message = "Maximo 3 familiares por estudiante")
        List<@Valid FamiliarRequest> familiares) {
}
