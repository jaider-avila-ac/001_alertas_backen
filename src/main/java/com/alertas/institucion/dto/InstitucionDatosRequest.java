package com.alertas.institucion.dto;

import com.alertas.shared.validacion.CodigoDane;
import com.alertas.shared.validacion.Correo;
import com.alertas.shared.validacion.Direccion;
import com.alertas.shared.validacion.Lugar;
import com.alertas.shared.validacion.Telefono;
import com.alertas.shared.validacion.TextoBasico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstitucionDatosRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
        @TextoBasico(message = "El nombre tiene caracteres que no se permiten")
        String nombre,

        @NotBlank(message = "El enlace (slug) es obligatorio")
        @Size(min = 3, max = 60, message = "El enlace debe tener entre 3 y 60 caracteres")
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "El enlace solo lleva minusculas, numeros y guiones, sin espacios")
        String slug,

        @CodigoDane
        String codigoDane,

        @Size(max = 80, message = "El municipio es muy largo")
        @Lugar(message = "El municipio solo lleva letras")
        String municipio,

        @Size(max = 80, message = "El departamento es muy largo")
        @Lugar(message = "El departamento solo lleva letras")
        String departamento,

        @Size(max = 150, message = "La direccion es muy larga")
        @Direccion
        String direccion,

        @Telefono
        String telefono,

        @Size(max = 120, message = "El correo es muy largo")
        @Correo
        String correo) {
}
