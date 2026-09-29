package com.alertas.notificacion.service;

import com.alertas.notificacion.dto.NotificacionResponse;
import org.springframework.web.socket.WebSocketSession;

// conexiones abiertas de websocket y envio al instante.
// el aviso pasa por redis: asi le llega al usuario aunque este conectado a otro servidor del backend
public interface EnVivoService {

    void conectar(WebSocketSession sesion, long institucionId, long usuarioId);

    void desconectar(WebSocketSession sesion);

    // se envia cuando la transaccion termina bien (si se deshace, no sale nada)
    void avisarNueva(long institucionId, long usuarioId, NotificacionResponse notificacion);

    // el usuario marco leidas: sus otras pestanas actualizan el contador
    void avisarLeidas(long institucionId, long usuarioId);

    // lo llama redis con cada aviso publicado por cualquier servidor
    void alRecibir(String mensaje);
}
