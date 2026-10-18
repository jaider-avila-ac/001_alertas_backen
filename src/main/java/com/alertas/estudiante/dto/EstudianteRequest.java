package com.alertas.estudiante.dto;

import com.alertas.shared.validacion.Celular;
import com.alertas.shared.validacion.ConDocumento;
import com.alertas.shared.validacion.ConLetras;
import com.alertas.shared.validacion.Correo;
import com.alertas.shared.validacion.Direccion;
import com.alertas.shared.validacion.DocumentoValido;
import com.alertas.shared.validacion.Nombre;
import com.alertas.shared.validacion.TextoBasico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@DocumentoValido
public record EstudianteRequest(

        @NotBlank(message = "El tipo de documento es obligatorio")
        @Pattern(regexp = "RC|TI|CC|CE|PPT", message = "El tipo de documento no es valido")
        String tipoDoc,

        @NotBlank(message = "El numero de documento es obligatorio")
        String nroDoc,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres son muy largos")
        @Nombre(message = "Los nombres solo llevan letras")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos son muy largos")
        @Nombre(message = "Los apellidos solo llevan letras")
        String apellidos,

        @Pattern(regexp = "F|M|O", message = "El genero no es valido")
        String genero,

        LocalDate fechaNacimiento,

        @Celular
        String celular,

        @Size(max = 120, message = "El correo es muy largo")
        @Correo
        String correo,

        @Size(max = 150, message = "La direccion es muy larga")
        @Direccion
        String direccion,

        @Size(max = 80, message = "El barrio es muy largo")
        @TextoBasico(message = "El barrio tiene caracteres que no se permiten")
        String barrio,

        @Size(max = 80, message = "La EPS es muy larga")
        @TextoBasico(message = "La EPS tiene caracteres que no se permiten")
        String eps,

        @Pattern(regexp = "(O|A|B|AB)[+-]", message = "El RH no es valido")
        String rh,

        @Size(max = 500, message = "Las condiciones de salud son muy largas (maximo 500 letras)")
        @ConLetras(message = "Las condiciones de salud deben tener letras")
        String condicionesSalud,

        @Size(max = 60, message = "El grupo es muy largo")
        @TextoBasico(message = "El grupo tiene caracteres que no se permiten")
        String grupo) implements ConDocumento {
}
