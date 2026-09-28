package com.alertas.personal.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.personal.model.Personal;
import com.alertas.personal.repository.PersonalRepository;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalServiceImpl implements PersonalService {

    private final PersonalRepository repository;
    private final UsuarioService usuarioService;
    private final EntityManager em;

    public PersonalServiceImpl(PersonalRepository repository, UsuarioService usuarioService, EntityManager em) {

        this.repository = repository;
        this.usuarioService = usuarioService;
        this.em = em;
    }

    @Override
    @Transactional
    public AdministradorResponse crearAdministrador(AdministradorRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        String documento = request.nroDoc().trim();

        if (repository.existsByNroDoc(documento) || usuarioService.existeDocumento(documento)) {
            throw ApiException.conflicto("Ya existe una persona con el documento " + documento);
        }

        Usuario usuario = usuarioService.crear(documento, Rol.ADMIN);

        Personal personal = new Personal();
        personal.setInstitucionId(institucionId);
        personal.setCodigo(CodigoAleatorio.generar());
        personal.setUsuario(usuario);
        personal.setTipoDoc(request.tipoDoc());
        personal.setNroDoc(documento);
        personal.setNombres(request.nombres().trim());
        personal.setApellidos(request.apellidos().trim());
        personal.setCorreo(vacioANull(request.correo()));
        personal.setCelular(vacioANull(request.celular()));

        repository.save(personal);
        return AdministradorResponse.desde(personal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdministradorResponse> listarAdministradores() {

        TenantSupport.requireTenant(em);

        // son pocos por institucion, no hace falta paginar
        List<Personal> administradores = repository.buscarPorRol(Rol.ADMIN);
        List<AdministradorResponse> respuesta = new ArrayList<>();

        for (Personal personal : administradores) {
            respuesta.add(AdministradorResponse.desde(personal));
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public AdministradorResponse buscarAdministrador(String codigo) {
        return AdministradorResponse.desde(obtenerAdministrador(codigo));
    }

    @Override
    @Transactional(readOnly = true)
    public Long usuarioIdDeAdministrador(String codigo) {
        return obtenerAdministrador(codigo).getUsuario().getId();
    }

    private Personal obtenerAdministrador(String codigo) {

        TenantSupport.requireTenant(em);

        Personal personal = repository.buscarPorCodigo(codigo);

        if (personal == null || personal.getUsuario().getRol() != Rol.ADMIN) {
            throw ApiException.noEncontrado("El administrador no existe en esta institucion");
        }

        return personal;
    }

    @Override
    @Transactional(readOnly = true)
    public NombrePersona buscarNombre(Long usuarioId) {

        TenantSupport.requireTenant(em);

        Personal personal = repository.buscarPorUsuario(usuarioId);

        if (personal == null) {
            return null;
        }

        return new NombrePersona(personal.getNombres(), personal.getApellidos());
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
