package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.service.LimiteIntentosService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LimiteIntentosServiceImpl implements LimiteIntentosService {

    private final StringRedisTemplate redis;
    private final int maxIntentos;
    // en un colegio muchos estudiantes salen por la misma ip publica, por eso la ip aguanta mas
    private final int maxIntentosIp;
    private final Duration bloqueo;

    public LimiteIntentosServiceImpl(
            StringRedisTemplate redis,
            @Value("${app.login.max-intentos}") int maxIntentos,
            @Value("${app.login.max-intentos-ip}") int maxIntentosIp,
            @Value("${app.login.bloqueo-minutos}") long bloqueoMinutos) {

        this.redis = redis;
        this.maxIntentos = maxIntentos;
        this.maxIntentosIp = maxIntentosIp;
        this.bloqueo = Duration.ofMinutes(bloqueoMinutos);
    }

    @Override
    public boolean estaBloqueado(String usuario, String ip) {

        String valorUsuario = redis.opsForValue().get("login:" + usuario);

        if (valorUsuario != null) {
            int intentosUsuario = Integer.parseInt(valorUsuario);

            if (intentosUsuario >= maxIntentos) {
                return true;
            }
        }

        String valorIp = redis.opsForValue().get("login:" + ip);

        if (valorIp != null) {
            int intentosIp = Integer.parseInt(valorIp);

            if (intentosIp >= maxIntentosIp) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void registrarFallo(String usuario, String ip) {

        String claveUsuario = "login:" + usuario;

        Long intentosUsuario =
                redis.opsForValue().increment(claveUsuario);

        if (intentosUsuario != null && intentosUsuario == 1) {
            redis.expire(claveUsuario, bloqueo);
        }

        String claveIp = "login:" + ip;

        Long intentosIp =
                redis.opsForValue().increment(claveIp);

        if (intentosIp != null && intentosIp == 1) {
            redis.expire(claveIp, bloqueo);
        }
    }

    @Override
    public void limpiar(String usuario) {
        redis.delete("login:" + usuario);
    }

    @Override
    public long getBloqueoMinutos() {
        return bloqueo.toMinutes();
    }
}
