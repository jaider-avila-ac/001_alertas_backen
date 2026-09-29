package com.alertas.personal.dto;

import com.alertas.personal.model.Personal;
import com.alertas.usuario.model.Usuario;
import java.time.OffsetDateTime;

public record PersonalDetalleResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String correo,
        String celular,
        String rol,
        boolean activo,
        boolean debeCambiarContrasena,
        OffsetDateTime ultimoIngreso,
        long casosAbiertos) {

    // casosAbiertos: estudiantes con alertas activas que atiende (solo psicorientadores)
    public static PersonalDetalleResponse desde(Personal personal, long casosAbiertos) {

        Usuario usuario = personal.getUsuario();

        return new PersonalDetalleResponse(
                personal.getCodigo(),
                personal.getTipoDoc(),
                personal.getNroDoc(),
                personal.getNombres(),
                personal.getApellidos(),
                personal.getCorreo(),
                personal.getCelular(),
                usuario.getRol().name(),
                usuario.isActivo(),
                usuario.isDebeCambiarContrasena(),
                usuario.getUltimoIngreso(),
                casosAbiertos);
    }
}
