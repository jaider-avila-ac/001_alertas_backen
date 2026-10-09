package com.alertas.notificacion.config;

import com.alertas.notificacion.service.EnVivoService;
import com.alertas.notificacion.service.TicketService;
import com.alertas.notificacion.service.serviceImpl.EnVivoServiceImpl;
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
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
 
// /ws/notificaciones?ticket=... ; el ticket se pide antes con el token (POST /api/v1/notificaciones/ticket)
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificacionesSocket socket;
    private final TicketService ticketService;
    private final String[] origenes;

    public WebSocketConfig(
            NotificacionesSocket socket,
            TicketService ticketService,
            @Value("${app.cors.origenes}") String origenes) {

        this.socket = socket;
        this.ticketService = ticketService;
        this.origenes = origenes.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(socket, "/ws/notificaciones")
                .addInterceptors(new ValidarTicket())
                .setAllowedOrigins(limpiar(origenes));
    }

    // cada servidor escucha el canal de redis y reenvia a sus conexiones
    @Bean
    public RedisMessageListenerContainer oyenteNotificaciones(RedisConnectionFactory conexion, EnVivoService enVivoService) {

        RedisMessageListenerContainer contenedor = new RedisMessageListenerContainer();
        contenedor.setConnectionFactory(conexion);
        contenedor.addMessageListener(
                (mensaje, patron) -> enVivoService.alRecibir(new String(mensaje.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(EnVivoServiceImpl.CANAL));
        return contenedor;
    }

    // sin ticket valido no hay conexion. el ticket dice de que colegio y usuario es.
    // nombre completo: el editor borraba el import al guardar
    private class ValidarTicket implements org.springframework.web.socket.server.HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> atributos) {

            if (!(request instanceof ServletServerHttpRequest)) {
                return false;
            }

            String ticket = ((ServletServerHttpRequest) request).getServletRequest().getParameter("ticket");
            long[] datos = ticketService.canjear(ticket);

            if (datos == null) {
                return false;
            }

            atributos.put("institucionId", datos[0]);
            atributos.put("usuarioId", datos[1]);
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
