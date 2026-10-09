package com.alertas.sesion.service.serviceImpl;

import com.alertas.auth.model.SesionAbierta;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.SesionService;
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.sesion.dto.ResumenSesionesResponse;
import com.alertas.sesion.dto.SesionActivaResponse;
import com.alertas.sesion.dto.SesionEventoResponse;
import com.alertas.sesion.service.SesionesEnVivoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Service
public class SesionesEnVivoServiceImpl implements SesionesEnVivoService {

    private static final Logger LOG = LoggerFactory.getLogger(SesionesEnVivoServiceImpl.class);
    private static final Duration DURACION_TICKET = Duration.ofSeconds(30);
    private static final int TIEMPO_ENVIO_MS = 5000;
    private static final int LIMITE_BUFFER = 64 * 1024;

    private final StringRedisTemplate redis;
    private final InstitucionService institucionService;
    private final SesionService sesionService;
    private final ObjectMapper mapper;

    // conexiones de superadmins abiertas en este servidor (son pocas)
    private final Set<WebSocketSession> conexiones = ConcurrentHashMap.newKeySet();

    public SesionesEnVivoServiceImpl(
            StringRedisTemplate redis,
            InstitucionService institucionService,
            SesionService sesionService,
            ObjectMapper mapper) {

        this.redis = redis;
        this.institucionService = institucionService;
        this.sesionService = sesionService;
        this.mapper = mapper;
    }

    @Override
    public String crearTicket() {

        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(claveTicket(ticket), UsuarioAutenticado.actual().id().toString(), DURACION_TICKET);
        return ticket;
    }

    @Override
    public Long canjearTicket(String ticket) {

        if (ticket == null || ticket.isBlank()) {
            return null;
        }

        // se lee y se borra: no se puede usar dos veces
        String valor = redis.opsForValue().getAndDelete(claveTicket(ticket));

        if (valor == null) {
            return null;
        }

        return Long.valueOf(valor);
    }

    @Override
    public void conectar(WebSocketSession sesion) {

        WebSocketSession segura = new ConcurrentWebSocketSessionDecorator(sesion, TIEMPO_ENVIO_MS, LIMITE_BUFFER);
        sesion.getAttributes().put("segura", segura);
        conexiones.add(segura);
    }

    @Override
    public void desconectar(WebSocketSession sesion) {

        Object segura = sesion.getAttributes().get("segura");

        if (segura != null) {
            conexiones.remove(segura);
        }
    }

    @Override
    public void alRecibir(String texto) {

        // si nadie tiene el panel abierto en este servidor no se hace nada
        if (conexiones.isEmpty()) {
            return;
        }

        // "institucion|tipo|codigo|rol"
        String[] partes = texto.split("\\|", -1);

        if (partes.length < 4) {
            return;
        }

        Long institucionId = Long.valueOf(partes[0]);
        String tipo = partes[1];
        String codigo = partes[2];

        EstadoInstitucion institucion = institucionService.estadoPorId(institucionId);

        if (institucion == null) {
            return;
        }

        List<SesionAbierta> abiertas = sesionService.listar(institucionId);
        Instant limite = Instant.now().minus(SesionService.EN_LINEA);

        // la fila que cambio. cerrada, rol y todas no la llevan
        SesionActivaResponse fila = null;
        if ("abierta".equals(tipo) || "actividad".equals(tipo)) {
            for (SesionAbierta sesion : abiertas) {
                if (sesion.codigo().equals(codigo)) {
                    fila = SesionActivaResponse.desde(sesion, limite);
                }
            }
            if (fila == null) {
                // se cerro mientras llegaba el aviso: ya llegara el de cerrada
                return;
            }
        }

        // al panel le llega el slug, nunca el id de la institucion
        SesionEventoResponse evento = new SesionEventoResponse(
                tipo, institucion.slug(), codigo, partes[3], fila, ResumenSesionesResponse.desde(abiertas, limite));

        TextMessage mensaje;
        try {
            mensaje = new TextMessage(mapper.writeValueAsString(evento));
        } catch (JsonProcessingException e) {
            LOG.warn("No se pudo armar el aviso de sesiones: {}", e.getMessage());
            return;
        }

        for (WebSocketSession sesion : conexiones) {
            enviar(sesion, mensaje);
        }
    }

    private void enviar(WebSocketSession sesion, TextMessage mensaje) {

        if (!sesion.isOpen()) {
            return;
        }

        try {
            sesion.sendMessage(mensaje);
        } catch (IOException | RuntimeException e) {
            LOG.debug("No se pudo enviar a una conexion del superadmin: {}", e.getMessage());
        }
    }

    private String claveTicket(String ticket) {
        return "ws:ticket-sa:" + ticket;
    }
}
