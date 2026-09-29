package com.alertas.estadistica.service;

import com.alertas.estadistica.dto.ComparativoResponse;
import com.alertas.estadistica.dto.EstadisticasGlobalesResponse;
import com.alertas.shared.dto.PageResponse;
import java.time.LocalDate;

// estadisticas del superadmin: cruzan colegios pero solo con totales
public interface EstadisticaGlobalService {

    // slug null = todos los colegios
    EstadisticasGlobalesResponse resumen(String slug, LocalDate desde, LocalDate hasta);

    PageResponse<ComparativoResponse> comparativo(LocalDate desde, LocalDate hasta, int pagina, int tamanio);
}
