package com.alertas.atencion.dto;

import com.alertas.shared.validacion.ConLetras;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// a quien se envia el caso (policia, icbf, eps, comisaria...) y por que
public record RemisionRequest(

        @NotBlank(message = "Escribe la entidad a la que se remite")
        @Size(max = 120, message = "La entidad es muy larga")
        @ConLetras(message = "La entidad debe tener letras")
        String entidad,

        @Size(max = 1000, message = "El motivo es muy largo")
        @ConLetras(message = "El motivo debe tener letras")
        String motivo) {
}
