package com.alertas.estadistica.dto;

import java.util.List;

// anioId: el anio que se aplico (null si son todos los anios). filtros: las listas para los selectores
public record EstadisticasResponse(
        Long anioId,
        IndicadoresResponse indicadores,
        List<ConteoResponse> porMes,
        List<ConteoResponse> porCategoria,
        List<ConteoResponse> porNivel,
        List<ConteoResponse> porGrupo,
        List<ConteoResponse> porGenero,
        List<ConteoResponse> porEdad,
        List<ConteoResponse> porOrigen,
        List<PsicorientadorConteoResponse> porPsicorientador,
        FiltrosDisponiblesResponse filtros) {
}
