package com.alertas.matricula.dto;

// un grupo del anio activo: cuantos faltan por pasar y a donde irian por defecto.
// seGradua: es el ultimo grado, sus estudiantes se graduan
public record GrupoPromocion(
        Long grupoId,
        String gradoNombre,
        String grupoNombre,
        long porPromover,
        long yaPromovidos,
        long graduados,
        boolean seGradua,
        Long destinoSugeridoId) {
}
