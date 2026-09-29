package com.alertas.notificacion.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.notificacion.service.TicketService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Duration DURACION = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public TicketServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String crear() {

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();
        String ticket = UUID.randomUUID().toString();

        redis.opsForValue().set(clave(ticket), usuario.institucionId() + ":" + usuario.id(), DURACION);
        return ticket;
    }

    @Override
    public long[] canjear(String ticket) {

        if (ticket == null || ticket.isBlank()) {
            return null;
        }

        // se lee y se borra: no se puede usar dos veces
        String valor = redis.opsForValue().getAndDelete(clave(ticket));

        if (valor == null) {
            return null;
        }

        String[] partes = valor.split(":");
        return new long[] {Long.parseLong(partes[0]), Long.parseLong(partes[1])};
    }

    private String clave(String ticket) {
        return "ws:ticket:" + ticket;
    }
}
