package com.alertas.bitacora.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.bitacora.model.Bitacora;
import com.alertas.bitacora.repository.BitacoraRepository;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.shared.TenantSupport;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class BitacoraServiceImpl implements BitacoraService {

    private final BitacoraRepository repository;
    private final EntityManager em;

    public BitacoraServiceImpl(BitacoraRepository repository, EntityManager em) {

        this.repository = repository;
        this.em = em;
    }

    @Override
    @Transactional
    public void registrar(String accion, String entidad, Long entidadId, String detalle) {

        Long institucionId = TenantSupport.requireTenant(em);
        UsuarioAutenticado autor = UsuarioAutenticado.actual();

        if (autor == null) {
            throw new IllegalStateException("La bitacora necesita un usuario autenticado");
        }

        Bitacora registro = new Bitacora();
        registro.setInstitucionId(institucionId);
        registro.setAccion(accion);
        registro.setEntidad(entidad);
        registro.setEntidadId(entidadId);
        registro.setDetalle(detalle);
        registro.setIp(ipActual());

        if (autor.esSuperadmin()) {
            registro.setSuperadminId(autor.id());
        } else {
            registro.setUsuarioId(autor.id());
        }

        repository.save(registro);
    }

    private String ipActual() {

        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();

        if (!(atributos instanceof ServletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) atributos).getRequest();
        return request.getRemoteAddr();
    }
}
