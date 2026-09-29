package com.alertas.notificacion.service;

import com.alertas.notificacion.dto.NotificacionResponse;
import com.alertas.shared.dto.PageResponse;
import java.util.Collection;

// avisos dentro de la app. los demas modulos la llaman; esta no depende de ninguno
public interface NotificacionService {

    // enlace: ruta del front sin el slug (puede ser null)
    void notificar(Long usuarioId, String tipo, String titulo, String mensaje, String enlace);

    void notificarVarios(Collection<Long> usuarioIds, String tipo, String titulo, String mensaje, String enlace);

    // ---- las del usuario de la sesion ----

    PageResponse<NotificacionResponse> mias(int pagina, int tamanio);

    long noLeidas();

    NotificacionResponse marcarLeida(Long id);

    int marcarTodasLeidas();
}
