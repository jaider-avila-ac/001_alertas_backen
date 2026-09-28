package com.alertas.institucion.dto;

import com.alertas.institucion.model.Institucion;
import java.time.OffsetDateTime;

// sin id: el superadmin y el front usan el slug
public record InstitucionResponse(
        String nombre,
        String slug,
        String enlace,
        String codigoDane,
        String municipio,
        String departamento,
        String direccion,
        String telefono,
        String correo,
        boolean activa,
        boolean accesoEstudiantes,
        boolean smsActivo,
        OffsetDateTime inactivadaEn,
        String motivoInactivacion,
        OffsetDateTime creadoEn) {

    // urlFront es la direccion del front de las instituciones, ej https://alertas.com
    public static InstitucionResponse desde(Institucion institucion, String urlFront) {

        return new InstitucionResponse(
                institucion.getNombre(),
                institucion.getSlug(),
                urlFront + "/" + institucion.getSlug(),
                institucion.getCodigoDane(),
                institucion.getMunicipio(),
                institucion.getDepartamento(),
                institucion.getDireccion(),
                institucion.getTelefono(),
                institucion.getCorreo(),
                institucion.isActiva(),
                institucion.isAccesoEstudiantes(),
                institucion.isSmsActivo(),
                institucion.getInactivadaEn(),
                institucion.getMotivoInactivacion(),
                institucion.getCreadoEn());
    }
}
