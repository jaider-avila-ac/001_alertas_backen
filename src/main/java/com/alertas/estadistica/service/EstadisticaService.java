package com.alertas.estadistica.service;

import com.alertas.estadistica.dto.EstadisticasResponse;
import com.alertas.estadistica.dto.FiltroEstadistica;

public interface EstadisticaService {

    EstadisticasResponse resumen(FiltroEstadistica filtro);

    // el mismo resumen en un excel de varias hojas. se arma en memoria y no se guarda
    byte[] excel(FiltroEstadistica filtro);
}
