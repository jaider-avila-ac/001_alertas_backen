package com.alertas.institucion.dto;

import com.alertas.personal.dto.AdministradorRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// la institucion siempre se crea con su primer administrador
public record CrearInstitucionRequest(

        @NotNull(message = "Faltan los datos de la institucion")
        @Valid
        InstitucionDatosRequest institucion,

        @NotNull(message = "Faltan los datos del administrador")
        @Valid
        AdministradorRequest administrador) {
}
