package com.alertas.sesion.config;

import com.alertas.auth.service.SesionService;
import com.alertas.sesion.service.SesionesEnVivoService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// /ws/superadmin?ticket=... ; el ticket se pide antes con el token (POST /api/v1/superadmin/sesiones/ticket)
@Configuration
public class SesionesSocketConfig implements WebSocketConfigurer {

    private final SesionesSocket socket;
    private final SesionesEnVivoService enVivoService;
    private final String[] origenes;

    public SesionesSocketConfig(
            SesionesSocket socket,
            SesionesEnVivoService enVivoService,
            @Value("${app.cors.origenes}") String origenes) {

        this.socket = socket;
        this.enVivoService = enVivoService;
        this.origenes = origenes.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(socket, "/ws/superadmin")
                .addInterceptors(new ValidarTicket())
                .setAllowedOrigins(limpiar(origenes));
    }

    // cada servidor escucha los cambios de sesiones y avisa a los paneles conectados a el
    @Bean
    public RedisMessageListenerContainer oyenteSesiones(RedisConnectionFactory conexion) {

        RedisMessageListenerContainer contenedor = new RedisMessageListenerContainer();
        contenedor.setConnectionFactory(conexion);
        contenedor.addMessageListener(
                (mensaje, patron) -> enVivoService.alRecibir(new String(mensaje.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(SesionService.CANAL_CAMBIOS));
        return contenedor;
    }

    // nombre completo: el editor borraba el import al guardar
    private class ValidarTicket implements org.springframework.web.socket.server.HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> atributos) {

            if (!(request instanceof ServletServerHttpRequest)) {
                return false;
            }

            String ticket = ((ServletServerHttpRequest) request).getServletRequest().getParameter("ticket");
            Long superadminId = enVivoService.canjearTicket(ticket);

            if (superadminId == null) {
                return false;
            }

            atributos.put("superadminId", superadminId);
            return true;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception error) {
            // nada
        }
    }

    private String[] limpiar(String[] lista) {

        String[] limpia = new String[lista.length];
        for (int i = 0; i < lista.length; i++) {
            limpia[i] = lista[i].trim();
        }
        return limpia;
    }
}
