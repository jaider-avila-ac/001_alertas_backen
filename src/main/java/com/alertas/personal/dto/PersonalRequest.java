package com.alertas.personal.dto;

import com.alertas.shared.validacion.Celular;
import com.alertas.shared.validacion.ConDocumento;
import com.alertas.shared.validacion.Correo;
import com.alertas.shared.validacion.DocumentoValido;
import com.alertas.shared.validacion.Nombre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// docente o psicorientador. los administradores se crean desde el superadmin
@DocumentoValido
public record PersonalRequest(

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

        @Size(max = 120, message = "El correo es muy largo")
        @Correo
        String correo,

        @Celular
        String celular,

        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "DOCENTE|PSICORIENTADOR", message = "El rol debe ser docente o psicorientador")
        String rol) implements ConDocumento {
}
