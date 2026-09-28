package com.alertas.estudiante.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FamiliarRequest(

        @NotBlank(message = "El nombre del familiar es obligatorio")
        @Size(max = 80, message = "El nombre del familiar es muy largo")
        String nombres,

        @Size(max = 80, message = "Los apellidos del familiar son muy largos")
        String apellidos,

        @NotBlank(message = "El parentesco es obligatorio")
        @Pattern(regexp = "MADRE|PADRE|ACUDIENTE|ABUELO|HERMANO|TIO|OTRO", message = "El parentesco no es valido")
        String parentesco,

        @Pattern(regexp = "3[0-9]{9}", message = "El celular del familiar debe tener 10 digitos y empezar por 3")
        String celular,

        boolean recibeSms) {
}
