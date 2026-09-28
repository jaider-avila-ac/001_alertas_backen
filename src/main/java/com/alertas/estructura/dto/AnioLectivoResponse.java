package com.alertas.estructura.dto;

import com.alertas.estructura.model.AnioLectivo;

public record AnioLectivoResponse(Long id, int anio, boolean activo, long totalGrupos) {

    public static AnioLectivoResponse desde(AnioLectivo anio, long totalGrupos) {
        return new AnioLectivoResponse(anio.getId(), anio.getAnio(), anio.isActivo(), totalGrupos);
    }
}
