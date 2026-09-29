package com.alertas.cita.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

// finalizar la cita: una observacion y un estado por cada alerta
public record ResultadoCitaRequest(

        @NotEmpty(message = "Falta el resultado de las alertas")
        @Valid
        List<ResultadoAlerta> resultados) {
}
