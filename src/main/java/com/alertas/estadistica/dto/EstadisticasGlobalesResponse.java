package com.alertas.estadistica.dto;

import com.alertas.shared.dto.PageResponse;
import java.util.List;

// solo agregados: ni nombres de estudiantes ni contenido de alertas.
// comparativo: la primera pagina, para que la pantalla salga con una sola peticion
public record EstadisticasGlobalesResponse(
        ResumenGlobalResponse resumen,
        List<ConteoResponse> usuariosPorRol,
        List<ConteoResponse> porMes,
        List<ConteoResponse> porCategoria,
        List<SmsMesResponse> smsPorMes,
        PageResponse<ComparativoResponse> comparativo) {
}
