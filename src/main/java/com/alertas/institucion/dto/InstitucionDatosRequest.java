package com.alertas.institucion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstitucionDatosRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre es muy largo")
        String nombre,

        @NotBlank(message = "El enlace (slug) es obligatorio")
        @Size(min = 3, max = 60, message = "El enlace debe tener entre 3 y 60 caracteres")
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "El enlace solo lleva minusculas, numeros y guiones, sin espacios")
        String slug,

        @Size(max = 20, message = "El codigo DANE es muy largo")
        @Pattern(regexp = "[0-9]*", message = "El codigo DANE solo lleva numeros")
        String codigoDane,

        @Size(max = 80, message = "El municipio es muy largo")
        String municipio,

        @Size(max = 80, message = "El departamento es muy largo")
        String departamento,

        @Size(max = 150, message = "La direccion es muy larga")
        String direccion,

        @Size(max = 20, message = "El telefono es muy largo")
        String telefono,

        @Email(message = "El correo no es valido")
        @Size(max = 120, message = "El correo es muy largo")
        String correo) {
}
