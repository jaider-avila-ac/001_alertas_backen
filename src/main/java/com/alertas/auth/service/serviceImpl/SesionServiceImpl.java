package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.SesionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        marcarCierre(claveUsuario(institucionId, usuarioId));
    }

    @Override
    public void cerrarSesionesDelRol(Long institucionId, Rol rol) {
        marcarCierre(claveRol(institucionId, rol));
    }

    @Override
    public boolean sigueVigente(UsuarioAutenticado usuario) {

        List<String> claves = new ArrayList<>();
        claves.add(claveUsuario(usuario.institucionId(), usuario.id()));

        if (usuario.institucionId() != null) {
            claves.add(claveRol(usuario.institucionId(), usuario.rol()));
        }

        // una sola ida a redis para las dos claves
        List<String> valores = redis.opsForValue().multiGet(claves);

        if (valores == null) {
            return true;
        }

        long emitidoEn = usuario.emitidoEn().toEpochMilli();

        for (String valor : valores) {
            if (valor != null && emitidoEn < Long.parseLong(valor)) {
                return false;
            }
        }

        return true;
    }

    private void marcarCierre(String clave) {

        String ahora = String.valueOf(Instant.now().toEpochMilli());

        // la clave dura lo mismo que un token, despues ya no hace falta
        redis.opsForValue().set(clave, ahora, jwtService.getDuracion());
    }

    private String claveUsuario(Long institucionId, Long usuarioId) {

        String ins = "sa";

        if (institucionId != null) {
            ins = institucionId.toString();
        }

        return "sesion:inval:" + ins + ":" + usuarioId;
    }

    private String claveRol(Long institucionId, Rol rol) {
        return "sesion:inval:rol:" + institucionId + ":" + rol.name();
    }
}
