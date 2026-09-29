package com.alertas.notificacion.service.serviceImpl;

import com.alertas.notificacion.dto.NotificacionResponse;
import com.alertas.notificacion.service.EnVivoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Service
public class EnVivoServiceImpl implements EnVivoService {

    public static final String CANAL = "notificaciones";

    private static final Logger LOG = LoggerFactory.getLogger(EnVivoServiceImpl.class);
    private static final int TIEMPO_ENVIO_MS = 5000;
    private static final int LIMITE_BUFFER = 64 * 1024;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    // "institucion:usuario" -> sus conexiones abiertas en este servidor (varias pestanas o equipos)
    private final Map<String, Set<WebSocketSession>> conexiones = new ConcurrentHashMap<>();

    public EnVivoServiceImpl(StringRedisTemplate redis, ObjectMapper mapper) {

        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void conectar(WebSocketSession sesion, long institucionId, long usuarioId) {

        // la sesion de spring no se puede usar desde dos hilos a la vez: el decorador lo resuelve
        WebSocketSession segura = new ConcurrentWebSocketSessionDecorator(sesion, TIEMPO_ENVIO_MS, LIMITE_BUFFER);
        sesion.getAttributes().put("segura", segura);

        conexiones.computeIfAbsent(clave(institucionId, usuarioId), k -> ConcurrentHashMap.newKeySet()).add(segura);
    }

    @Override
    public void desconectar(WebSocketSession sesion) {

        Object segura = sesion.getAttributes().get("segura");
        Object institucion = sesion.getAttributes().get("institucionId");
        Object usuario = sesion.getAttributes().get("usuarioId");

        if (segura == null || institucion == null || usuario == null) {
            return;
        }

        String clave = clave((Long) institucion, (Long) usuario);
        Set<WebSocketSession> suyas = conexiones.get(clave);

        if (suyas != null) {
            suyas.remove(segura);
            if (suyas.isEmpty()) {
                conexiones.remove(clave);
            }
        }
    }

    @Override
    public void avisarNueva(long institucionId, long usuarioId, NotificacionResponse notificacion) {

        ObjectNode mensaje = mapper.createObjectNode();
        mensaje.put("tipo", "nueva");
        mensaje.set("notificacion", mapper.valueToTree(notificacion));
        publicarAlTerminar(institucionId, usuarioId, mensaje);
    }

    @Override
    public void avisarLeidas(long institucionId, long usuarioId) {

        ObjectNode mensaje = mapper.createObjectNode();
        mensaje.put("tipo", "leidas");
        publicarAlTerminar(institucionId, usuarioId, mensaje);
    }

    @Override
    public void alRecibir(String texto) {

        try {
            JsonNode sobre = mapper.readTree(texto);
            String clave = clave(sobre.get("institucionId").asLong(), sobre.get("usuarioId").asLong());
            Set<WebSocketSession> suyas = conexiones.get(clave);

            if (suyas == null) {
                return;
            }

            TextMessage mensaje = new TextMessage(mapper.writeValueAsString(sobre.get("mensaje")));

            for (WebSocketSession sesion : suyas) {
                enviar(sesion, mensaje);
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Aviso de notificacion invalido: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- ayudas

    private void publicarAlTerminar(long institucionId, long usuarioId, ObjectNode mensaje) {

        ObjectNode sobre = mapper.createObjectNode();
        sobre.put("institucionId", institucionId);
        sobre.put("usuarioId", usuarioId);
        sobre.set("mensaje", mensaje);

        String texto;
        try {
            texto = mapper.writeValueAsString(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el aviso", e);
        }

        // si la transaccion se deshace el aviso no sale
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicar(texto);
                }
            });
        } else {
            publicar(texto);
        }
    }

    private void publicar(String texto) {

        try {
            redis.convertAndSend(CANAL, texto);
        } catch (RuntimeException e) {
            // sin redis no llega al instante, pero la notificacion ya quedo guardada
            LOG.warn("No se pudo publicar la notificacion: {}", e.getMessage());
        }
    }

    private void enviar(WebSocketSession sesion, TextMessage mensaje) {

        if (!sesion.isOpen()) {
            return;
        }

        try {
            sesion.sendMessage(mensaje);
        } catch (IOException | RuntimeException e) {
            LOG.debug("No se pudo enviar a una conexion: {}", e.getMessage());
        }
    }

    private String clave(long institucionId, long usuarioId) {
        return institucionId + ":" + usuarioId;
    }
}
