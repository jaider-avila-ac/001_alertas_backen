package com.alertas.alerta.service;

import com.alertas.alerta.dto.AlertaDetalleResponse;
import com.alertas.alerta.dto.AlertaFilaResponse;
import com.alertas.alerta.dto.CrearAlertaRequest;
import com.alertas.alerta.dto.SolicitudAyudaRequest;
import com.alertas.shared.dto.PageResponse;

// alertas de la institucion del contexto. afuera se identifican por su codigo
public interface AlertaService {

    // la crea un docente (o psicorientador o admin). nace pendiente
    AlertaDetalleResponse crear(CrearAlertaRequest request);

    // la crea el estudiante para si mismo
    AlertaDetalleResponse solicitarAyuda(SolicitudAyudaRequest request);

    // las que creo el usuario de la sesion. estado null = todas
    PageResponse<AlertaFilaResponse> misReportadas(String estado, int pagina, int tamanio);

    // todas las de la institucion, para el admin. estado y texto null = sin filtro
    PageResponse<AlertaFilaResponse> todas(String estado, String texto, int pagina, int tamanio);

    // la ve quien la creo, el psicorientador y el admin
    AlertaDetalleResponse buscar(String codigo);
}
