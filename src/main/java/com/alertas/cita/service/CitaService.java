package com.alertas.cita.service;

import com.alertas.cita.dto.AgendarCitaRequest;
import com.alertas.cita.dto.CancelarCitaRequest;
import com.alertas.cita.dto.CitaResponse;
import com.alertas.cita.dto.ReprogramarCitaRequest;
import com.alertas.cita.dto.ResultadoCitaRequest;
import java.time.LocalDate;
import java.util.List;

// citas del psicorientador de la sesion. afuera se identifican por su codigo
public interface CitaService {

    // incluye las alertas activas del estudiante (menos las excluidas); las pendientes pasan a en proceso
    CitaResponse agendar(AgendarCitaRequest request);

    CitaResponse buscar(String codigo);

    // se puede iniciar cuando se quiera, la hora es solo una referencia
    CitaResponse iniciar(String codigo);

    // realizada: por cada alerta, su observacion y si sigue en proceso o quedo completada
    CitaResponse finalizar(String codigo, ResultadoCitaRequest request);

    // en estos tres las alertas no cambian de estado
    CitaResponse noAsistio(String codigo);

    CitaResponse cancelar(String codigo, CancelarCitaRequest request);

    CitaResponse reprogramar(String codigo, ReprogramarCitaRequest request);

    // agenda del psicorientador de la sesion entre dos fechas (maximo 6 semanas)
    List<CitaResponse> agenda(LocalDate desde, LocalDate hasta);

    // ---- para otros modulos ----

    // todas las citas del estudiante, de la mas reciente a la mas vieja
    List<CitaResponse> delEstudiante(Long estudianteId, boolean conObservacion);

    // al reasignar: la cita programada pasa al nuevo psicorientador (falla si se le cruza con otra)
    void pasarAOtroPsicorientador(Long estudianteId, Long psicorientadorId);
}
