package com.alertas.institucion.service.serviceImpl;

import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.model.Institucion;
import com.alertas.institucion.repository.InstitucionRepository;
import com.alertas.institucion.service.InstitucionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitucionServiceImpl implements InstitucionService {

    private static final Duration DURACION_CACHE = Duration.ofMinutes(10);

    private final InstitucionRepository repository;
    private final StringRedisTemplate redis;

    public InstitucionServiceImpl(InstitucionRepository repository, StringRedisTemplate redis) {

        this.repository = repository;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    @Override
    public EstadoInstitucion estadoPorSlug(String slug) {

        String clave = "inst:slug:" + slug;
        String guardado = redis.opsForValue().get(clave);

        if (guardado != null) {
            return EstadoInstitucion.desdeTexto(guardado);
        }

        Institucion institucion = repository.findBySlug(slug).orElse(null);

        if (institucion == null) {
            return null;
        }

        EstadoInstitucion estado = EstadoInstitucion.desde(institucion);
        redis.opsForValue().set(clave, estado.aTexto(), DURACION_CACHE);
        return estado;
    }

    @Transactional(readOnly = true)
    @Override
    public EstadoInstitucion estadoPorId(Long id) {

        String clave = "inst:id:" + id;
        String guardado = redis.opsForValue().get(clave);

        if (guardado != null) {
            return EstadoInstitucion.desdeTexto(guardado);
        }

        Institucion institucion = repository.findById(id).orElse(null);

        if (institucion == null) {
            return null;
        }

        EstadoInstitucion estado = EstadoInstitucion.desde(institucion);
        redis.opsForValue().set(clave, estado.aTexto(), DURACION_CACHE);
        return estado;
    }

    @Override
    public void olvidarCache(Long id, String slug) {

        List<String> claves = new ArrayList<>();
        claves.add("inst:id:" + id);
        claves.add("inst:slug:" + slug);

        redis.delete(claves);
    }
}
