package com.alertas.estructura.dto;

import com.alertas.estructura.model.Grupo;

public record GrupoResponse(
        Long id,
        String nombre,
        Long anioId,
        int anio,
        Long gradoId,
        String gradoNombre,
        int gradoOrden,
        long totalEstudiantes,
        long totalAlertas) {

    public static GrupoResponse desde(Grupo grupo, long totalEstudiantes, long totalAlertas) {

        return new GrupoResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getAnio().getId(),
                grupo.getAnio().getAnio(),
                grupo.getGrado().getId(),
                grupo.getGrado().getNombre(),
                grupo.getGrado().getOrden(),
                totalEstudiantes,
                totalAlertas);
    }
}
