package com.alertas.estudiante.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// grupoId solo se usa al crear (el cambio de grupo tiene su propio endpoint)
public record EstudianteRequest(

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

        @Pattern(regexp = "F|M|O", message = "El genero no es valido")
        String genero,

        LocalDate fechaNacimiento,

        @Pattern(regexp = "3[0-9]{9}", message = "El celular debe tener 10 digitos y empezar por 3")
        String celular,

        @Email(message = "El correo no es valido")
        @Size(max = 120, message = "El correo es muy largo")
        String correo,

        @Size(max = 150, message = "La direccion es muy larga")
        String direccion,

        @Size(max = 80, message = "El barrio es muy largo")
        String barrio,

        @Size(max = 80, message = "La EPS es muy larga")
        String eps,

        @Pattern(regexp = "(O|A|B|AB)[+-]", message = "El RH no es valido")
        String rh,

        @Size(max = 500, message = "Las condiciones de salud son muy largas (maximo 500 letras)")
        String condicionesSalud,

        Long grupoId) {
}
