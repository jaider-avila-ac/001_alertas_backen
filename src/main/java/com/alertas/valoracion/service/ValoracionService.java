package com.alertas.valoracion.service;

import com.alertas.shared.dto.PageResponse;
import com.alertas.valoracion.dto.EstudianteValoracionResponse;
import com.alertas.valoracion.dto.RegistrarValoracionRequest;
import com.alertas.valoracion.dto.ValoracionResponse;
import com.alertas.valoracion.dto.ValoracionesConfigResponse;
import java.util.List;

public interface ValoracionService {

    ValoracionesConfigResponse configuracion();

    // filtro: TODOS, POR_VALORAR o NUNCA. texto, grupo y edad (clave de RangoEdad) pueden venir null
    PageResponse<EstudianteValoracionResponse> estudiantes(
            String filtro, String texto, String grupo, String edad, int pagina, int tamanio);

    ValoracionResponse registrar(RegistrarValoracionRequest request);

    // para el expediente. la observacion solo va para psicorientadores
    List<ValoracionResponse> delEstudiante(Long estudianteId, boolean conObservacion);
}
