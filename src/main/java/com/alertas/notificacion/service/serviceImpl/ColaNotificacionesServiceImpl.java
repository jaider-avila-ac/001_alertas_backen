package com.alertas.notificacion.service.serviceImpl;

import com.alertas.notificacion.dto.NotificacionResponse;
import com.alertas.notificacion.model.Notificacion;
import com.alertas.notificacion.repository.NotificacionRepository;
import com.alertas.notificacion.service.ColaNotificacionesService;
import com.alertas.notificacion.service.EnVivoService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.interceptor.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ColaNotificacionesServiceImpl implements ColaNotificacionesService {

    private static final Logger LOG = LoggerFactory.getLogger(ColaNotificacionesServiceImpl.class);

    private static final int MAXIMO_INTENTOS = 5;
    // los mensajes ya confirmados no se necesitan: la cola se recorta a este tamanio
    private static final long TAMANIO_COLA = 10000;

    private final StringRedisTemplate redis;
    private final NotificacionRepository repository;
    private final EnVivoService enVivoService;
    private final TransactionTemplate transaccion;
    private final EntityManager em;
    private final String consumidor = "backend-" + UUID.randomUUID();

    // un mensaje sin confirmar por este tiempo se da por perdido y se reintenta (las pruebas lo bajan)
    private Duration esperaReintento = Duration.ofSeconds(30);

    public ColaNotificacionesServiceImpl(
            StringRedisTemplate redis,
            NotificacionRepository repository,
            EnVivoService enVivoService,
            PlatformTransactionManager transacciones,
            EntityManager em) {

        this.redis = redis;
        this.repository = repository;
        this.enVivoService = enVivoService;
        // transaccion propia: al encolar sin redis se guarda despues del commit de la accion
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.em = em;
    }

    @Override
    public String consumidor() {
        return consumidor;
    }

    // ---------------------------------------------------------------- encolar

    @Override
    public void encolar(long institucionId, long usuarioId, String tipo, String titulo, String mensaje, String enlace) {

        Map<String, String> campos = new HashMap<>();
        campos.put("institucionId", String.valueOf(institucionId));
        campos.put("usuarioId", String.valueOf(usuarioId));
        campos.put("tipo", tipo);
        campos.put("titulo", titulo);
        campos.put("mensaje", mensaje);
        if (enlace != null) {
            campos.put("enlace", enlace);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agregar(campos);
                }
            });
        } else {
            agregar(campos);
        }
    }

    private void agregar(Map<String, String> campos) {

        try {
            redis.opsForStream().add(StreamRecords.newRecord().in(COLA).ofMap(campos));
        } catch (RuntimeException e) {
            // sin redis no hay cola: se guarda de una vez para no perder el aviso
            LOG.warn("No se pudo encolar la notificacion, se guarda directo: {}", e.getMessage());
            guardar(campos, null);
        }
    }

    // ---------------------------------------------------------------- consumir

    @Override
    public void procesar(MapRecord<String, String, String> registro) {

        Map<String, String> campos = registro.getValue();
        NotificacionResponse creada;

        try {
            creada = guardar(campos, registro.getId().getValue());
        } finally {
            // el consumidor corre en su propio hilo: no debe quedar con el colegio de este mensaje
            TenantContext.limpiar();
        }

        redis.opsForStream().acknowledge(COLA, GRUPO, registro.getId());

        // null: ya se habia guardado en un intento anterior (y ya se aviso)
        if (creada != null) {
            enVivoService.avisarNueva(
                    Long.parseLong(campos.get("institucionId")), Long.parseLong(campos.get("usuarioId")), creada);
        }
    }

    // en su propia transaccion
    private NotificacionResponse guardar(Map<String, String> campos, String colaId) {
        return transaccion.execute(estado -> guardarEnColegio(campos, colaId));
    }

    // guarda la notificacion dentro del colegio del mensaje. null si ya estaba guardada
    private NotificacionResponse guardarEnColegio(Map<String, String> campos, String colaId) {

        Long institucionId = Long.parseLong(campos.get("institucionId"));
        TenantSupport.usarInstitucion(em, institucionId);

        if (colaId != null && repository.existsByColaId(colaId)) {
            return null;
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setInstitucionId(institucionId);
        notificacion.setUsuarioId(Long.parseLong(campos.get("usuarioId")));
        notificacion.setTipo(campos.get("tipo"));
        notificacion.setTitulo(campos.get("titulo"));
        notificacion.setMensaje(campos.get("mensaje"));
        notificacion.setEnlace(campos.get("enlace"));
        notificacion.setColaId(colaId);
        repository.save(notificacion);
        return NotificacionResponse.desde(notificacion);
    }

    // ---------------------------------------------------------------- reintentos

    @Override
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void reintentarPendientes() {

        try {
            PendingMessages pendientes = redis.opsForStream().pending(COLA, GRUPO, Range.unbounded(), 100);

            for (PendingMessage pendiente : pendientes) {
                if (pendiente.getElapsedTimeSinceLastDelivery().compareTo(esperaReintento) < 0) {
                    continue;
                }

                if (pendiente.getTotalDeliveryCount() >= MAXIMO_INTENTOS) {
                    pasarAFallidas(pendiente.getId());
                    continue;
                }

                // lo toma este servidor (el que lo tenia pudo haberse caido) y lo intenta de nuevo
                List<MapRecord<String, Object, Object>> tomados =
                        redis.opsForStream().claim(COLA, GRUPO, consumidor, esperaReintento, pendiente.getId());

                for (MapRecord<String, Object, Object> tomado : tomados) {
                    reprocesar(tomado);
                }
            }

            redis.opsForStream().trim(COLA, TAMANIO_COLA, true);
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron revisar las notificaciones pendientes: {}", e.getMessage());
        }
    }

    private void reprocesar(MapRecord<String, Object, Object> tomado) {

        Map<String, String> campos = new HashMap<>();
        for (Map.Entry<Object, Object> campo : tomado.getValue().entrySet()) {
            campos.put(String.valueOf(campo.getKey()), String.valueOf(campo.getValue()));
        }

        try {
            procesar(StreamRecords.newRecord().in(COLA).withId(tomado.getId()).ofMap(campos));
        } catch (RuntimeException e) {
            LOG.warn("Reintento fallido de la notificacion {}: {}", tomado.getId(), e.getMessage());
        }
    }

    // despues de varios intentos se aparta para revisarla a mano y se confirma para que no se repita mas
    private void pasarAFallidas(RecordId id) {

        List<MapRecord<String, Object, Object>> registros = redis.opsForStream().range(COLA, Range.just(id.getValue()));

        for (MapRecord<String, Object, Object> registro : registros) {
            Map<String, String> campos = new HashMap<>();
            for (Map.Entry<Object, Object> campo : registro.getValue().entrySet()) {
                campos.put(String.valueOf(campo.getKey()), String.valueOf(campo.getValue()));
            }
            campos.put("idOriginal", id.getValue());
            redis.opsForStream().add(StreamRecords.newRecord().in(FALLIDAS).ofMap(campos));
        }

        redis.opsForStream().acknowledge(COLA, GRUPO, id);
        LOG.error("Notificacion {} apartada en {} despues de {} intentos", id, FALLIDAS, MAXIMO_INTENTOS);
    }
}
