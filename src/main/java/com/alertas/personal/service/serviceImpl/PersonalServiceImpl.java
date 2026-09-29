package com.alertas.personal.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.personal.dto.ActualizarPersonalRequest;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.personal.dto.EstadoMasivoPersonalRequest;
import com.alertas.personal.dto.PersonalDetalleResponse;
import com.alertas.personal.dto.PersonalFilaResponse;
import com.alertas.personal.dto.PersonalRequest;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.dto.PsicorientadoresInactivadosEvento;
import com.alertas.personal.model.Personal;
import com.alertas.personal.repository.PersonalFila;
import com.alertas.personal.repository.PersonalRepository;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalServiceImpl implements PersonalService {

    private static final int TAMANIO_MAXIMO = 50;

    private final PersonalRepository repository;
    private final UsuarioService usuarioService;
    private final ApplicationEventPublisher eventos;
    private final EntityManager em;

    public PersonalServiceImpl(
            PersonalRepository repository,
            UsuarioService usuarioService,
            ApplicationEventPublisher eventos,
            EntityManager em) {

        this.repository = repository;
        this.usuarioService = usuarioService;
        this.eventos = eventos;
        this.em = em;
    }

    // ---------------------------------------------------------------- administradores

    @Override
    @Transactional
    public AdministradorResponse crearAdministrador(AdministradorRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        String documento = request.nroDoc().trim();

        validarDocumentoLibre(documento);

        Usuario usuario = usuarioService.crear(documento, Rol.ADMIN);

        Personal personal = nuevaPersona(institucionId, usuario);
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

    // ---------------------------------------------------------------- docentes y psicorientadores

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PersonalFilaResponse> listar(String texto, String rol, Boolean activo, int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        String busqueda = null;

        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<PersonalFila> page = repository.buscar(busqueda, rol, activo, pageRequest);

        List<PersonalFilaResponse> contenido = new ArrayList<>();

        for (PersonalFila fila : page.getContent()) {
            contenido.add(PersonalFilaResponse.desde(fila));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalDetalleResponse buscar(String codigo) {

        TenantSupport.requireTenant(em);
        return detalle(obtener(codigo));
    }

    @Override
    @Transactional
    public PersonalDetalleResponse crear(PersonalRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        String documento = request.nroDoc().trim();

        validarDocumentoLibre(documento);

        Usuario usuario = usuarioService.crear(documento, Rol.valueOf(request.rol()));

        Personal personal = nuevaPersona(institucionId, usuario);
        copiarDatos(request, personal);
        repository.save(personal);

        return detalle(personal);
    }

    @Override
    @Transactional
    public PersonalDetalleResponse actualizar(String codigo, ActualizarPersonalRequest request) {

        TenantSupport.requireTenant(em);

        Personal personal = obtener(codigo);
        String documento = request.nroDoc().trim();
        Long usuarioId = personal.getUsuario().getId();

        if (!documento.equals(personal.getNroDoc())) {
            if (repository.existsByNroDocAndIdNot(documento, personal.getId())) {
                throw ApiException.conflicto("Ya existe una persona con el documento " + documento);
            }
            usuarioService.cambiarDocumento(usuarioId, documento);
        }

        personal.setTipoDoc(request.tipoDoc());
        personal.setNroDoc(documento);
        personal.setNombres(request.nombres().trim());
        personal.setApellidos(request.apellidos().trim());
        personal.setCorreo(vacioANull(request.correo()));
        personal.setCelular(vacioANull(request.celular()));

        return detalle(personal);
    }

    @Override
    @Transactional
    public PersonalDetalleResponse restablecerContrasena(String codigo) {

        TenantSupport.requireTenant(em);

        Personal personal = obtener(codigo);
        usuarioService.restablecerContrasenaPorAdmin(personal.getUsuario().getId());

        return detalle(personal);
    }

    @Override
    @Transactional
    public PersonalDetalleResponse cambiarEstado(String codigo, boolean activo) {

        TenantSupport.requireTenant(em);

        Long institucionId = TenantSupport.requireTenant(em);

        Personal personal = obtener(codigo);
        usuarioService.cambiarEstadoPorAdmin(personal.getUsuario().getId(), activo);

        // sus casos abiertos vuelven a la bandeja para que otro psicorientador los tome
        if (!activo && personal.getUsuario().getRol() == Rol.PSICORIENTADOR) {
            eventos.publishEvent(new PsicorientadoresInactivadosEvento(institucionId));
        }

        return detalle(personal);
    }

    @Override
    @Transactional
    public int cambiarEstadoMasivo(EstadoMasivoPersonalRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        boolean activo = request.activo();
        int afectados;

        if (request.codigos() != null && !request.codigos().isEmpty()) {
            List<Long> usuarioIds = repository.usuarioIdsPorCodigos(request.codigos());

            if (usuarioIds.isEmpty()) {
                return 0;
            }

            afectados = usuarioService.cambiarEstadoMasivo(null, usuarioIds, activo);
        } else if (request.rol() != null) {
            afectados = usuarioService.cambiarEstadoMasivo(Rol.valueOf(request.rol()), null, activo);
        } else if (request.todos()) {
            int docentes = usuarioService.cambiarEstadoMasivo(Rol.DOCENTE, null, activo);
            int psicorientadores = usuarioService.cambiarEstadoMasivo(Rol.PSICORIENTADOR, null, activo);
            afectados = docentes + psicorientadores;
        } else {
            throw ApiException.invalido("Selecciona personas, un rol o todos");
        }

        // si entre los inactivados hay psicorientadores, sus casos vuelven a la bandeja
        if (!activo && afectados > 0) {
            eventos.publishEvent(new PsicorientadoresInactivadosEvento(institucionId));
        }

        return afectados;
    }

    // ---------------------------------------------------------------- para otros modulos

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

    @Override
    @Transactional(readOnly = true)
    public PsicorientadorBasico psicorientadorDeUsuario(Long usuarioId) {

        TenantSupport.requireTenant(em);

        Personal personal = repository.buscarPorUsuario(usuarioId);

        if (personal == null || personal.getUsuario().getRol() != Rol.PSICORIENTADOR) {
            return null;
        }

        return basico(personal);
    }

    @Override
    @Transactional(readOnly = true)
    public PsicorientadorBasico psicorientadorActivo(String codigo) {

        TenantSupport.requireTenant(em);

        Personal personal = repository.buscarPorCodigo(codigo);

        if (personal == null || personal.getUsuario().getRol() != Rol.PSICORIENTADOR || !personal.getUsuario().isActivo()) {
            throw ApiException.noEncontrado("El psicorientador no existe o esta inactivo");
        }

        return basico(personal);
    }

    @Override
    @Transactional(readOnly = true)
    public PsicorientadorBasico psicorientadorPorId(Long personalId) {

        TenantSupport.requireTenant(em);

        Personal personal = repository.findById(personalId).orElse(null);

        if (personal == null) {
            return null;
        }

        return basico(personal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PsicorientadorBasico> psicorientadoresActivos() {

        TenantSupport.requireTenant(em);

        List<PsicorientadorBasico> respuesta = new ArrayList<>();

        for (Personal personal : repository.buscarPorRol(Rol.PSICORIENTADOR)) {
            if (personal.getUsuario().isActivo()) {
                respuesta.add(basico(personal));
            }
        }

        return respuesta;
    }

    private PsicorientadorBasico basico(Personal personal) {
        return new PsicorientadorBasico(
                personal.getId(), personal.getCodigo(), personal.getNombres(), personal.getApellidos(),
                personal.getUsuario().getId());
    }

    // ---------------------------------------------------------------- ayudas

    private PersonalDetalleResponse detalle(Personal personal) {

        long casos = 0;
        if (personal.getUsuario().getRol() == Rol.PSICORIENTADOR) {
            casos = repository.casosAbiertos(personal.getId());
        }

        return PersonalDetalleResponse.desde(personal, casos);
    }

    // docente o psicorientador. un administrador por aqui "no existe": lo maneja el superadmin
    private Personal obtener(String codigo) {

        Personal personal = repository.buscarPorCodigo(codigo);

        if (personal == null || personal.getUsuario().getRol() == Rol.ADMIN) {
            throw ApiException.noEncontrado("La persona no existe");
        }

        return personal;
    }

    private void validarDocumentoLibre(String documento) {

        if (repository.existsByNroDoc(documento) || usuarioService.existeDocumento(documento)) {
            throw ApiException.conflicto("Ya existe una persona con el documento " + documento);
        }
    }

    private Personal nuevaPersona(Long institucionId, Usuario usuario) {

        Personal personal = new Personal();
        personal.setInstitucionId(institucionId);
        personal.setCodigo(CodigoAleatorio.generar());
        personal.setUsuario(usuario);
        return personal;
    }

    private void copiarDatos(PersonalRequest request, Personal personal) {

        personal.setTipoDoc(request.tipoDoc());
        personal.setNroDoc(request.nroDoc().trim());
        personal.setNombres(request.nombres().trim());
        personal.setApellidos(request.apellidos().trim());
        personal.setCorreo(vacioANull(request.correo()));
        personal.setCelular(vacioANull(request.celular()));
    }

    private int limitarTamanio(int tamanio) {

        if (tamanio < 1) {
            return 20;
        }

        if (tamanio > TAMANIO_MAXIMO) {
            return TAMANIO_MAXIMO;
        }

        return tamanio;
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
