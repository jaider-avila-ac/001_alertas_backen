package com.alertas.notificacion.config;

import com.alertas.notificacion.service.ColaNotificacionesService;
import java.time.Duration;
import java.util.function.Predicate;
import org.springframework.core.NestedExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ErrorHandler;

// consumidor de la cola de notificaciones. cada servidor del backend entra al mismo grupo:
// redis le da cada mensaje a uno solo y los que nadie confirma se reintentan
@Configuration
@EnableScheduling
public class ColaNotificacionesConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ColaNotificacionesConfig.class);

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> consumidorNotificaciones(
            RedisConnectionFactory conexion, StringRedisTemplate redis, ColaNotificacionesService cola) {

        crearGrupo(redis);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> opciones =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .batchSize(20)
                        .errorHandler(new AvisarError())
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> contenedor =
                StreamMessageListenerContainer.create(conexion, opciones);

        // sin confirmacion automatica: se confirma despues de guardar.
        // un error leyendo (redis se reinicio, el equipo se suspendio) no apaga el consumidor: sigue leyendo
        StreamMessageListenerContainer.StreamReadRequest<String> lectura = StreamMessageListenerContainer.StreamReadRequest
                .builder(StreamOffset.create(ColaNotificacionesService.COLA, ReadOffset.lastConsumed()))
                .consumer(Consumer.from(ColaNotificacionesService.GRUPO, cola.consumidor()))
                .autoAcknowledge(false)
                .cancelOnError(new SeguirSiFalla())
                .errorHandler(new AvisarError())
                .build();
        contenedor.register(lectura, new Oyente(cola));

        return contenedor;
    }

    // el grupo se crea una vez (con la cola si no existe). si ya existe redis responde BUSYGROUP
    private void crearGrupo(StringRedisTemplate redis) {

        try {
            redis.opsForStream().createGroup(ColaNotificacionesService.COLA, ReadOffset.from("0"), ColaNotificacionesService.GRUPO);
        } catch (DataAccessException e) {
            String causa = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (causa == null || !causa.contains("BUSYGROUP")) {
                LOG.warn("No se pudo crear el grupo de la cola de notificaciones: {}", causa);
            }
        }
    }

    // ante cualquier error la lectura no se cancela
    private static class SeguirSiFalla implements Predicate<Throwable> {

        @Override
        public boolean test(Throwable error) {
            return false;
        }
    }

    private static class Oyente implements StreamListener<String, MapRecord<String, String, String>> {

        private final ColaNotificacionesService cola;

        Oyente(ColaNotificacionesService cola) {
            this.cola = cola;
        }

        @Override
        public void onMessage(MapRecord<String, String, String> registro) {

            try {
                cola.procesar(registro);
            } catch (RuntimeException e) {
                // queda sin confirmar: reintentarPendientes lo toma despues
                LOG.warn("No se pudo procesar la notificacion {}: {}", registro.getId(), e.getMessage());
            }
        }
    }

    private static class AvisarError implements ErrorHandler {

        @Override
        public void handleError(Throwable error) {
            LOG.warn("Error leyendo la cola de notificaciones: {}", error.getMessage());
        }
    }
}
