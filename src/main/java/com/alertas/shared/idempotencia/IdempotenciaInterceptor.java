package com.alertas.shared.idempotencia;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotenciaInterceptor implements HandlerInterceptor {

    public static final String CABECERA = "Idempotency-Key";

    private static final Duration DURACION = Duration.ofMinutes(10);
    private static final String ATRIBUTO_CLAVE = "idempotencia.clave";

    private final StringRedisTemplate redis;

    public IdempotenciaInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod metodo = (HandlerMethod) handler;

        if (!metodo.hasMethodAnnotation(Idempotente.class)) {
            return true;
        }

        String llave = request.getHeader(CABECERA);

        if (llave == null || llave.isBlank() || llave.length() > 100) {
            throw ApiException.invalido("Falta la cabecera " + CABECERA);
        }

        String clave = "idem:" + dueno(request) + ":" + llave;
        Boolean esNueva = redis.opsForValue().setIfAbsent(clave, "1", DURACION);

        if (!Boolean.TRUE.equals(esNueva)) {
            throw ApiException.conflicto("Esta solicitud ya fue enviada, espera un momento");
        }

        request.setAttribute(ATRIBUTO_CLAVE, clave);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        Object clave = request.getAttribute(ATRIBUTO_CLAVE);

        if (clave == null) {
            return;
        }

        // si fallo se libera la llave para que el usuario pueda reintentar con los datos corregidos
        boolean fallo = ex != null || response.getStatus() >= 400;

        if (fallo) {
            redis.delete(clave.toString());
        }
    }

    // la misma llave de dos usuarios distintos no debe chocar
    private String dueno(HttpServletRequest request) {

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();

        if (usuario == null) {
            return "anonimo:" + request.getRemoteAddr();
        }

        if (usuario.institucionId() == null) {
            return "sa:" + usuario.id();
        }

        return usuario.institucionId() + ":" + usuario.id();
    }
}
