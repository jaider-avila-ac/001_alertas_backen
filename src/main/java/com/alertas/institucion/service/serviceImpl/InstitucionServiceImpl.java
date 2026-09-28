package com.alertas.institucion.service.serviceImpl;

import com.alertas.bitacora.service.BitacoraService;
import com.alertas.categoria.service.CategoriaService;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.institucion.dto.CrearInstitucionRequest;
import com.alertas.institucion.dto.CrearInstitucionResponse;
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.dto.InstitucionDatosRequest;
import com.alertas.institucion.dto.InstitucionPublicaResponse;
import com.alertas.institucion.dto.InstitucionResponse;
import com.alertas.institucion.model.Institucion;
import com.alertas.institucion.repository.InstitucionRepository;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InstitucionServiceImpl implements InstitucionService {

    private static final Duration DURACION_CACHE = Duration.ofMinutes(10);
    private static final int TAMANIO_MAXIMO = 50;

    // mismas rutas que la bd no deja usar como slug (ck_instituciones_slug_reservado)
    private static final Set<String> SLUGS_RESERVADOS =
            Set.of("superadmin", "api", "admin", "login", "assets", "static", "public");

    private final InstitucionRepository repository;
    private final EstructuraService estructuraService;
    private final CategoriaService categoriaService;
    private final PersonalService personalService;
    private final UsuarioService usuarioService;
    private final BitacoraService bitacoraService;
    private final StringRedisTemplate redis;
    private final EntityManager em;
    private final String urlFront;

    public InstitucionServiceImpl(
            InstitucionRepository repository,
            EstructuraService estructuraService,
            CategoriaService categoriaService,
            PersonalService personalService,
            UsuarioService usuarioService,
            BitacoraService bitacoraService,
            StringRedisTemplate redis,
            EntityManager em,
            @Value("${app.url-front}") String urlFront) {

        this.repository = repository;
        this.estructuraService = estructuraService;
        this.categoriaService = categoriaService;
        this.personalService = personalService;
        this.usuarioService = usuarioService;
        this.bitacoraService = bitacoraService;
        this.redis = redis;
        this.em = em;
        this.urlFront = urlFront;
    }

    // ---------------------------------------------------------------- estado con cache

    // instituciones no tiene RLS, por eso aqui no se pide tenant
    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional(readOnly = true)
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

    // ---------------------------------------------------------------- superadmin

    @Override
    @Transactional
    public CrearInstitucionResponse crear(CrearInstitucionRequest request) {

        InstitucionDatosRequest datos = request.institucion();

        validarSlug(datos.slug(), null);
        validarCodigoDane(datos.codigoDane(), null);

        Institucion institucion = new Institucion();
        copiarDatos(datos, institucion);
        repository.saveAndFlush(institucion);

        // de aqui en adelante todo se guarda dentro de la institucion nueva
        TenantSupport.usarInstitucion(em, institucion.getId());

        estructuraService.crearDatosIniciales();
        categoriaService.crearPredeterminadas();
        AdministradorResponse administrador = personalService.crearAdministrador(request.administrador());

        bitacoraService.registrar("CREAR_INSTITUCION", "institucion", institucion.getId(),
                "slug " + institucion.getSlug());

        return new CrearInstitucionResponse(InstitucionResponse.desde(institucion, urlFront), administrador);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InstitucionResponse> listar(String texto, Boolean activa, int pagina, int tamanio) {

        String busqueda = null;

        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<Institucion> page = repository.buscar(busqueda, activa, pageRequest);

        List<InstitucionResponse> contenido = new ArrayList<>();

        for (Institucion institucion : page.getContent()) {
            contenido.add(InstitucionResponse.desde(institucion, urlFront));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public InstitucionResponse buscar(String slug) {
        return InstitucionResponse.desde(obtenerPorSlug(slug), urlFront);
    }

    @Override
    @Transactional
    public InstitucionResponse actualizar(String slug, InstitucionDatosRequest request) {

        Institucion institucion = obtenerPorSlug(slug);
        Long id = institucion.getId();

        validarSlug(request.slug(), id);
        validarCodigoDane(request.codigoDane(), id);

        copiarDatos(request, institucion);

        TenantSupport.usarInstitucion(em, id);

        if (!slug.equals(institucion.getSlug())) {
            bitacoraService.registrar("CAMBIAR_ENLACE", "institucion", id,
                    "de " + slug + " a " + institucion.getSlug());
        }

        olvidarCache(id, slug);
        olvidarCache(id, institucion.getSlug());
        return InstitucionResponse.desde(institucion, urlFront);
    }

    @Override
    @Transactional
    public InstitucionResponse inactivar(String slug, String motivo) {

        Institucion institucion = obtenerPorSlug(slug);
        Long id = institucion.getId();

        if (!institucion.isActiva()) {
            throw ApiException.conflicto("La institucion ya esta inhabilitada");
        }

        institucion.inactivar(motivo.trim());

        TenantSupport.usarInstitucion(em, id);
        bitacoraService.registrar("INHABILITAR_INSTITUCION", "institucion", id, motivo.trim());

        // al borrar la cache, la siguiente solicitud de cualquier usuario del colegio ya ve que esta inactiva
        olvidarCache(id, institucion.getSlug());
        return InstitucionResponse.desde(institucion, urlFront);
    }

    @Override
    @Transactional
    public InstitucionResponse activar(String slug) {

        Institucion institucion = obtenerPorSlug(slug);
        Long id = institucion.getId();

        if (institucion.isActiva()) {
            throw ApiException.conflicto("La institucion ya esta habilitada");
        }

        institucion.activar();

        TenantSupport.usarInstitucion(em, id);
        bitacoraService.registrar("HABILITAR_INSTITUCION", "institucion", id, null);

        olvidarCache(id, institucion.getSlug());
        return InstitucionResponse.desde(institucion, urlFront);
    }

    @Override
    @Transactional
    public InstitucionResponse cambiarSms(String slug, boolean activo) {

        Institucion institucion = obtenerPorSlug(slug);
        Long id = institucion.getId();
        institucion.setSmsActivo(activo);

        TenantSupport.usarInstitucion(em, id);

        if (activo) {
            bitacoraService.registrar("ACTIVAR_SMS", "institucion", id, null);
        } else {
            bitacoraService.registrar("DESACTIVAR_SMS", "institucion", id, null);
        }

        return InstitucionResponse.desde(institucion, urlFront);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdministradorResponse> listarAdministradores(String slug) {

        usarInstitucionPorSlug(slug);
        return personalService.listarAdministradores();
    }

    @Override
    @Transactional
    public AdministradorResponse crearAdministrador(String slug, AdministradorRequest request) {

        usarInstitucionPorSlug(slug);

        AdministradorResponse administrador = personalService.crearAdministrador(request);
        Long usuarioId = personalService.usuarioIdDeAdministrador(administrador.codigo());
        bitacoraService.registrar("CREAR_ADMINISTRADOR", "usuario", usuarioId, null);

        return administrador;
    }

    @Override
    @Transactional
    public AdministradorResponse restablecerContrasenaAdministrador(String slug, String codigo) {

        usarInstitucionPorSlug(slug);

        // si el codigo no es de un administrador de esta institucion, falla aqui
        Long usuarioId = personalService.usuarioIdDeAdministrador(codigo);

        usuarioService.restablecerContrasena(usuarioId);
        bitacoraService.registrar("RESTABLECER_CONTRASENA", "usuario", usuarioId, null);

        return personalService.buscarAdministrador(codigo);
    }

    @Override
    @Transactional
    public AdministradorResponse asignarContrasenaAdministrador(String slug, String codigo, String nueva) {

        usarInstitucionPorSlug(slug);

        Long usuarioId = personalService.usuarioIdDeAdministrador(codigo);

        usuarioService.asignarContrasena(usuarioId, nueva);
        bitacoraService.registrar("ASIGNAR_CONTRASENA", "usuario", usuarioId, null);

        return personalService.buscarAdministrador(codigo);
    }

    @Override
    @Transactional
    public AdministradorResponse cambiarEstadoAdministrador(String slug, String codigo, boolean activo) {

        usarInstitucionPorSlug(slug);

        Long usuarioId = personalService.usuarioIdDeAdministrador(codigo);
        usuarioService.cambiarEstado(usuarioId, activo);

        if (activo) {
            bitacoraService.registrar("ACTIVAR_USUARIO", "usuario", usuarioId, null);
        } else {
            bitacoraService.registrar("INACTIVAR_USUARIO", "usuario", usuarioId, null);
        }

        return personalService.buscarAdministrador(codigo);
    }

    // ---------------------------------------------------------------- usuarios de la institucion

    @Override
    @Transactional(readOnly = true)
    public InstitucionPublicaResponse buscarPublica(Long id) {
        return InstitucionPublicaResponse.desde(obtener(id));
    }

    @Override
    @Transactional(readOnly = true)
    public InstitucionResponse miInstitucion() {

        Long institucionId = TenantSupport.requireTenant(em);
        return InstitucionResponse.desde(obtener(institucionId), urlFront);
    }

    @Override
    @Transactional
    public InstitucionResponse cambiarAccesoEstudiantes(boolean activo) {

        Long institucionId = TenantSupport.requireTenant(em);
        Institucion institucion = obtener(institucionId);

        institucion.setAccesoEstudiantes(activo);

        if (activo) {
            bitacoraService.registrar("ABRIR_ACCESO_ESTUDIANTES", "institucion", institucionId, null);
        } else {
            bitacoraService.registrar("CERRAR_ACCESO_ESTUDIANTES", "institucion", institucionId, null);
        }

        // el interceptor revisa este dato en cada solicitud, al borrar la cache aplica de inmediato
        olvidarCache(institucionId, institucion.getSlug());
        return InstitucionResponse.desde(institucion, urlFront);
    }

    // ---------------------------------------------------------------- ayudas

    private Institucion obtenerPorSlug(String slug) {

        Institucion institucion = repository.findBySlug(slug).orElse(null);

        if (institucion == null) {
            throw ApiException.noEncontrado("La institucion no existe");
        }

        return institucion;
    }

    // el superadmin elige la institucion por el slug de la url
    private void usarInstitucionPorSlug(String slug) {

        Institucion institucion = obtenerPorSlug(slug);
        TenantSupport.usarInstitucion(em, institucion.getId());
    }

    private Institucion obtener(Long id) {

        Institucion institucion = repository.findById(id).orElse(null);

        if (institucion == null) {
            throw ApiException.noEncontrado("La institucion no existe");
        }

        return institucion;
    }

    // idActual es null cuando se esta creando
    private void validarSlug(String slug, Long idActual) {

        if (SLUGS_RESERVADOS.contains(slug)) {
            throw ApiException.invalido("El enlace '" + slug + "' esta reservado, elige otro");
        }

        boolean enUso;

        if (idActual == null) {
            enUso = repository.existsBySlug(slug);
        } else {
            enUso = repository.existsBySlugAndIdNot(slug, idActual);
        }

        if (enUso) {
            throw ApiException.conflicto("El enlace '" + slug + "' ya lo usa otra institucion");
        }
    }

    private void validarCodigoDane(String codigoDane, Long idActual) {

        if (codigoDane == null || codigoDane.isBlank()) {
            return;
        }

        boolean enUso;

        if (idActual == null) {
            enUso = repository.existsByCodigoDane(codigoDane);
        } else {
            enUso = repository.existsByCodigoDaneAndIdNot(codigoDane, idActual);
        }

        if (enUso) {
            throw ApiException.conflicto("El codigo DANE " + codigoDane + " ya esta registrado");
        }
    }

    private void copiarDatos(InstitucionDatosRequest datos, Institucion institucion) {

        institucion.setNombre(datos.nombre().trim());
        institucion.setSlug(datos.slug());
        institucion.setCodigoDane(vacioANull(datos.codigoDane()));
        institucion.setMunicipio(vacioANull(datos.municipio()));
        institucion.setDepartamento(vacioANull(datos.departamento()));
        institucion.setDireccion(vacioANull(datos.direccion()));
        institucion.setTelefono(vacioANull(datos.telefono()));
        institucion.setCorreo(vacioANull(datos.correo()));
    }

    // se borra despues del commit. si se borra antes, otra solicitud alcanza a leer el dato viejo
    // de la bd y lo vuelve a guardar en cache por 10 minutos
    private void olvidarCache(Long id, String slug) {

        List<String> claves = new ArrayList<>();
        claves.add("inst:id:" + id);
        claves.add("inst:slug:" + slug);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.delete(claves);
            }
        });
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
