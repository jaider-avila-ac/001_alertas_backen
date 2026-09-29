package com.alertas.notificacion.service;

import org.springframework.data.redis.connection.stream.MapRecord;

// cola de notificaciones en redis streams. la accion solo encola; un consumidor guarda la notificacion,
// confirma el mensaje y la entrega en vivo. lo que falla se reintenta y despues de varios intentos
// pasa a la cola de fallidas
public interface ColaNotificacionesService {

    String COLA = "cola:notificaciones";
    String FALLIDAS = "cola:notificaciones:fallidas";
    String GRUPO = "notificadores";

    // se encola cuando la transaccion termina bien (si se deshace, no sale nada)
    void encolar(long institucionId, long usuarioId, String tipo, String titulo, String mensaje, String enlace);

    // lo llama el consumidor con cada mensaje. si lanza error el mensaje queda sin confirmar
    void procesar(MapRecord<String, String, String> registro);

    // mensajes que nadie confirmo (fallo o se cayo el servidor): se toman de nuevo
    void reintentarPendientes();

    // nombre de este servidor dentro del grupo de consumidores
    String consumidor();
}
