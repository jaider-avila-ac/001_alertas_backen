package com.alertas.estadistica.dto;

import java.util.List;

// solo agregados: ni nombres de estudiantes ni contenido de alertas
public record EstadisticasGlobalesResponse(
        ResumenGlobalResponse resumen,
        List<ConteoResponse> usuariosPorRol,
        List<ConteoResponse> porMes,
        List<ConteoResponse> porCategoria,
        List<SmsMesResponse> smsPorMes) {
}
