package com.alertas.personal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// sin rol: el rol se pone al crear la persona y no cambia
public record ActualizarPersonalRequest(

        @NotBlank(message = "El tipo de documento es obligatorio")
        @Pattern(regexp = "RC|TI|CC|CE|PPT", message = "El tipo de documento no es valido")
        String tipoDoc,

        @NotBlank(message = "El numero de documento es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9]{3,20}", message = "El documento solo lleva letras y numeros, de 3 a 20")
        String nroDoc,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres son muy largos")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos son muy largos")
        String apellidos,

        @Email(message = "El correo no es valido")
        @Size(max = 120, message = "El correo es muy largo")
        String correo,

        @Pattern(regexp = "3[0-9]{9}", message = "El celular debe tener 10 digitos y empezar por 3")
        String celular) {
}
