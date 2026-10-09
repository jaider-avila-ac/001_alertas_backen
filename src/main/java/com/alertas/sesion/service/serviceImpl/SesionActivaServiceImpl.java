package com.alertas.sesion.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.SesionAbierta;
import com.alertas.auth.service.SesionService;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.sesion.dto.CerrarSesionesResponse;
import com.alertas.sesion.dto.ResumenSesionesResponse;
import com.alertas.sesion.dto.SesionActivaResponse;
import com.alertas.sesion.dto.SesionesResponse;
import com.alertas.sesion.service.SesionActivaService;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SesionActivaServiceImpl implements SesionActivaService {

    private static final int TAMANIO_MAXIMO = 100;

    private final InstitucionService institucionService;
    private final SesionService sesionService;
    private final BitacoraService bitacoraService;

    public SesionActivaServiceImpl(
            InstitucionService institucionService, SesionService sesionService, BitacoraService bitacoraService) {

        this.institucionService = institucionService;
        this.sesionService = sesionService;
        this.bitacoraService = bitacoraService;
    }

    @Override
    @Transactional(readOnly = true)
    public SesionesResponse listar(String slug, String rol, int pagina, int tamanio) {

        Long institucionId = institucionService.usarPorSlug(slug);
        Rol filtro = leerRol(rol);

        if (pagina < 0) {
            pagina = 0;
        }
        if (tamanio < 1 || tamanio > TAMANIO_MAXIMO) {
            tamanio = 20;
        }

        List<SesionAbierta> abiertas = sesionService.listar(institucionId);
        Instant limite = Instant.now().minus(SesionService.EN_LINEA);

        List<SesionAbierta> filtradas = new ArrayList<>();
        for (SesionAbierta sesion : abiertas) {
            if (filtro == null || sesion.rol() == filtro) {
                filtradas.add(sesion);
            }
        }

        // la mas reciente primero (las nuevas que lleguen por el websocket tambien van arriba)
        filtradas.sort(new Comparator<SesionAbierta>() {
            @Override
            public int compare(SesionAbierta a, SesionAbierta b) {
                return b.inicio().compareTo(a.inicio());
            }
        });

        int total = filtradas.size();
        int desde = Math.min(pagina * tamanio, total);
        int hasta = Math.min(desde + tamanio, total);

        List<SesionActivaResponse> contenido = new ArrayList<>();
        for (SesionAbierta sesion : filtradas.subList(desde, hasta)) {
            contenido.add(SesionActivaResponse.desde(sesion, limite));
        }

        int totalPaginas = (total + tamanio - 1) / tamanio;
        PageResponse<SesionActivaResponse> paginaPedida =
                new PageResponse<>(contenido, pagina, tamanio, total, totalPaginas);

        return new SesionesResponse(ResumenSesionesResponse.desde(abiertas, limite), paginaPedida);
    }

    @Override
    @Transactional
    public void cerrar(String slug, String codigo) {

        Long institucionId = institucionService.usarPorSlug(slug);

        // se busca antes para saber de quien era (bitacora)
        SesionAbierta sesion = sesionService.buscar(institucionId, codigo);

        if (sesion == null || !sesionService.cerrar(institucionId, codigo)) {
            throw ApiException.noEncontrado("La sesion ya no esta abierta");
        }

        bitacoraService.registrar("CERRAR_SESION", "usuario", sesion.usuarioId(), null);
    }

    @Override
    @Transactional
    public CerrarSesionesResponse cerrarTodas(String slug) {

        Long institucionId = institucionService.usarPorSlug(slug);
        int cerradas = sesionService.cerrarTodas(institucionId);

        bitacoraService.registrar("CERRAR_SESIONES", "institucion", institucionId, cerradas + " sesiones");
        return new CerrarSesionesResponse(cerradas);
    }

    private Rol leerRol(String rol) {

        if (rol == null || rol.isBlank()) {
            return null;
        }

        Rol leido;

        try {
            leido = Rol.valueOf(rol.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalido("Rol no valido");
        }

        if (leido == Rol.SUPERADMIN) {
            throw ApiException.invalido("Rol no valido");
        }

        return leido;
    }
}
