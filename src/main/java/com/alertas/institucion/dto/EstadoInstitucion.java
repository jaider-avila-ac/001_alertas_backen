package com.alertas.institucion.dto;

import com.alertas.institucion.model.Institucion;
// lo minimo que se revisa en cada solicitud, va en cache
public record EstadoInstitucion(Long id, String slug, boolean activa, boolean accesoEstudiantes) {

    public static EstadoInstitucion desde(Institucion institucion) {

        return new EstadoInstitucion(
                institucion.getId(),
                institucion.getSlug(),
                institucion.isActiva(),
                institucion.isAccesoEstudiantes());
    }

    // en redis se guarda como "id|slug|activa|acceso"
    public String aTexto() {
        return id + "|" + slug + "|" + activa + "|" + accesoEstudiantes;
    }

    public static EstadoInstitucion desdeTexto(String texto) {

        String[] partes = texto.split("\\|");

        Long id = Long.valueOf(partes[0]);
        String slug = partes[1];
        boolean activa = Boolean.parseBoolean(partes[2]);
        boolean accesoEstudiantes = Boolean.parseBoolean(partes[3]);

        return new EstadoInstitucion(id, slug, activa, accesoEstudiantes);
    }
}
