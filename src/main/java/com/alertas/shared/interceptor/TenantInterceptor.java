package com.alertas.shared.interceptor;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// decide la institucion de cada solicitud:
//  - rutas publicas /api/v1/public/{slug}/...  -> por el slug de la url
//  - rutas con sesion /api/v1/...             -> por el token (nunca por el body)
//  - /api/v1/superadmin/...                   -> sin institucion
@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String CABECERA_SLUG = "X-Institucion-Slug";

    private static final Pattern RUTA_PUBLICA = Pattern.compile("^/api/v1/public/([a-z0-9-]+)(/.*)?$");
    private static final String NO_DISPONIBLE = "La institucion no esta disponible";

    private final InstitucionService institucionService;

    public TenantInterceptor(InstitucionService institucionService) {
        this.institucionService = institucionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String ruta = request.getRequestURI().substring(request.getContextPath().length());

        if (ruta.startsWith("/api/v1/superadmin/")) {
            return true;
        }

        Matcher publica = RUTA_PUBLICA.matcher(ruta);

        if (publica.matches()) {
            String slug = publica.group(1);
            EstadoInstitucion estado = institucionService.estadoPorSlug(slug);

            if (estado == null || !estado.activa()) {
                throw ApiException.noEncontrado(NO_DISPONIBLE);
            }

            TenantContext.establecer(estado.id(), estado.slug());
            return true;
        }

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();

        if (usuario == null) {
            // de esto se encarga spring security
            return true;
        }

        if (usuario.esSuperadmin()) {
            throw ApiException.prohibido("Esta ruta es solo para usuarios de una institucion");
        }

        EstadoInstitucion estado = institucionService.estadoPorId(usuario.institucionId());

        if (estado == null || !estado.activa()) {
            throw ApiException.prohibido(NO_DISPONIBLE);
        }

        if (usuario.rol() == Rol.ESTUDIANTE && !estado.accesoEstudiantes()) {
            throw ApiException.prohibido("El acceso de estudiantes esta deshabilitado en este momento");
        }

        // el front manda el slug de la url en la que esta. si no coincide con el del token, alguien
        // esta usando la sesion de un colegio en el enlace de otro
        String slugUrl = request.getHeader(CABECERA_SLUG);

        if (slugUrl != null && !slugUrl.equals(estado.slug())) {
            throw ApiException.prohibido("Tu sesion pertenece a otra institucion");
        }

        // si todavia usa la contrasena asignada no se le bloquea nada: el front solo le sugiere cambiarla
        TenantContext.establecer(estado.id(), estado.slug());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.limpiar();
    }
}
