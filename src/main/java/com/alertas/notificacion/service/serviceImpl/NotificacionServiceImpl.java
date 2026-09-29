package com.alertas.notificacion.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.notificacion.dto.NotificacionResponse;
import com.alertas.notificacion.model.Notificacion;
import com.alertas.notificacion.repository.NotificacionRepository;
import com.alertas.notificacion.service.EnVivoService;
import com.alertas.notificacion.service.NotificacionService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private static final int TAMANIO_MAXIMO = 50;

    private final NotificacionRepository repository;
    private final EnVivoService enVivoService;
    private final EntityManager em;

    public NotificacionServiceImpl(NotificacionRepository repository, EnVivoService enVivoService, EntityManager em) {

        this.repository = repository;
        this.enVivoService = enVivoService;
        this.em = em;
    }

    @Override
    @Transactional
    public void notificar(Long usuarioId, String tipo, String titulo, String mensaje, String enlace) {

        Long institucionId = TenantSupport.requireTenant(em);

        // nadie recibe aviso de lo que el mismo hizo
        UsuarioAutenticado actual = UsuarioAutenticado.actual();
        if (actual != null && actual.id().equals(usuarioId)) {
            return;
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setInstitucionId(institucionId);
        notificacion.setUsuarioId(usuarioId);
        notificacion.setTipo(tipo);
        notificacion.setTitulo(recortar(titulo, 120));
        notificacion.setMensaje(recortar(mensaje, 300));
        notificacion.setEnlace(enlace);
        repository.save(notificacion);

        // le llega al instante si tiene la app abierta
        enVivoService.avisarNueva(institucionId, usuarioId, NotificacionResponse.desde(notificacion));
    }

    @Override
    @Transactional
    public void notificarVarios(Collection<Long> usuarioIds, String tipo, String titulo, String mensaje, String enlace) {

        for (Long usuarioId : usuarioIds) {
            notificar(usuarioId, tipo, titulo, mensaje, enlace);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificacionResponse> mias(int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        int limite = tamanio;
        if (limite < 1 || limite > TAMANIO_MAXIMO) {
            limite = 20;
        }

        Page<Notificacion> page = repository.findByUsuarioIdOrderByCreadoEnDesc(
                usuarioActual(), PageRequest.of(Math.max(pagina, 0), limite));

        List<NotificacionResponse> contenido = new ArrayList<>();
        for (Notificacion notificacion : page.getContent()) {
            contenido.add(NotificacionResponse.desde(notificacion));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long noLeidas() {

        TenantSupport.requireTenant(em);
        return repository.countByUsuarioIdAndLeidaFalse(usuarioActual());
    }

    @Override
    @Transactional
    public NotificacionResponse marcarLeida(Long id) {

        TenantSupport.requireTenant(em);

        // solo las propias: la de otro usuario "no existe"
        Notificacion notificacion = repository.findByIdAndUsuarioId(id, usuarioActual());

        if (notificacion == null) {
            throw ApiException.noEncontrado("La notificacion no existe");
        }

        notificacion.setLeida(true);
        enVivoService.avisarLeidas(notificacion.getInstitucionId(), notificacion.getUsuarioId());
        return NotificacionResponse.desde(notificacion);
    }

    @Override
    @Transactional
    public int marcarTodasLeidas() {

        Long institucionId = TenantSupport.requireTenant(em);

        int marcadas = repository.marcarTodasLeidas(usuarioActual());
        enVivoService.avisarLeidas(institucionId, usuarioActual());
        return marcadas;
    }

    private Long usuarioActual() {
        return UsuarioAutenticado.actual().id();
    }

    private String recortar(String texto, int maximo) {

        if (texto.length() <= maximo) {
            return texto;
        }

        return texto.substring(0, maximo - 3) + "...";
    }
}
