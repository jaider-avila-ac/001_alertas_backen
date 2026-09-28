package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.SesionService;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SesionServiceImpl implements SesionService {

    private final StringRedisTemplate redis;
    private final JwtService jwtService;

    public SesionServiceImpl(StringRedisTemplate redis, JwtService jwtService) {

        this.redis = redis;
        this.jwtService = jwtService;
    }

    @Override
    public void cerrarSesiones(Long institucionId, Long usuarioId) {

        String clave = clave(institucionId, usuarioId);
        String ahora = String.valueOf(Instant.now().toEpochMilli());

        // la clave dura lo mismo que un token, despues ya no hace falta
        redis.opsForValue().set(clave, ahora, jwtService.getDuracion());
    }

    @Override
    public boolean sigueVigente(UsuarioAutenticado usuario) {

        String valor = redis.opsForValue().get(clave(usuario.institucionId(), usuario.id()));

        if (valor == null) {
            return true;
        }

        long cerradasEn = Long.parseLong(valor);
        long emitidoEn = usuario.emitidoEn().toEpochMilli();

        return emitidoEn > cerradasEn;
    }

    private String clave(Long institucionId, Long usuarioId) {

        String ins = "sa";

        if (institucionId != null) {
            ins = institucionId.toString();
        }

        return "sesion:inval:" + ins + ":" + usuarioId;
    }
}
