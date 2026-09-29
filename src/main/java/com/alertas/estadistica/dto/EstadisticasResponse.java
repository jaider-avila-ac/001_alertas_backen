package com.alertas.estadistica.dto;

import java.util.List;

public record EstadisticasResponse(
        IndicadoresResponse indicadores,
        List<ConteoResponse> porMes,
        List<ConteoResponse> porCategoria,
        List<ConteoResponse> porNivel,
        List<ConteoResponse> porGrupo,
        List<ConteoResponse> porGenero,
        List<ConteoResponse> porEdad,
        List<ConteoResponse> porOrigen,
        List<PsicorientadorConteoResponse> porPsicorientador) {
}
