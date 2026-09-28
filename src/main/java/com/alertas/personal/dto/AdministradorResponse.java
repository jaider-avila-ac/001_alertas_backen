package com.alertas.personal.dto;

import com.alertas.personal.model.Personal;
import java.time.OffsetDateTime;

public record AdministradorResponse(
        Long usuarioId,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String correo,
        String celular,
        boolean activo,
        boolean debeCambiarContrasena,
        OffsetDateTime ultimoIngreso) {

    public static AdministradorResponse desde(Personal personal) {

        return new AdministradorResponse(
                personal.getUsuario().getId(),
                personal.getTipoDoc(),
                personal.getNroDoc(),
                personal.getNombres(),
                personal.getApellidos(),
                personal.getCorreo(),
                personal.getCelular(),
                personal.getUsuario().isActivo(),
                personal.getUsuario().isDebeCambiarContrasena(),
                personal.getUsuario().getUltimoIngreso());
    }
}
