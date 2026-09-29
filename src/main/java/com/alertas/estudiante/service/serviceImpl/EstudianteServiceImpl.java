package com.alertas.estudiante.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.estudiante.dto.EstadoMasivoEstudiantesRequest;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.dto.EstudianteDetalleResponse;
import com.alertas.estudiante.dto.EstudianteFilaResponse;
import com.alertas.estudiante.dto.EstudianteRequest;
import com.alertas.estudiante.dto.FamiliarRequest;
import com.alertas.estudiante.dto.FamiliarResponse;
import com.alertas.estudiante.dto.QrResponse;
import com.alertas.estudiante.model.Estudiante;
import com.alertas.estudiante.model.Familiar;
import com.alertas.estudiante.repository.EstudianteFila;
import com.alertas.estudiante.repository.EstudianteRepository;
import com.alertas.estudiante.repository.FamiliarRepository;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.matricula.dto.MatriculaResponse;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.shared.interceptor.TenantContext;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstudianteServiceImpl implements EstudianteService {

    private static final int TAMANIO_MAXIMO = 50;

    private final EstudianteRepository repository;
    private final MatriculaService matriculaService;
    private final FamiliarRepository familiarRepository;
    private final UsuarioService usuarioService;
    private final EstructuraService estructuraService;
    private final BitacoraService bitacoraService;
    private final EntityManager em;
    private final String urlFront;

    public EstudianteServiceImpl(
            EstudianteRepository repository,
            MatriculaService matriculaService,
            FamiliarRepository familiarRepository,
            UsuarioService usuarioService,
            EstructuraService estructuraService,
            BitacoraService bitacoraService,
            EntityManager em,
            @Value("${app.url-front}") String urlFront) {

        this.repository = repository;
        this.matriculaService = matriculaService;
        this.familiarRepository = familiarRepository;
        this.usuarioService = usuarioService;
        this.estructuraService = estructuraService;
        this.bitacoraService = bitacoraService;
        this.em = em;
        this.urlFront = urlFront;
    }

    // ---------------------------------------------------------------- consultar

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EstudianteFilaResponse> listar(
            String texto, Long gradoId, Long grupoId, Boolean activo, boolean sinGrupo, int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        Long anioId = idAnioActivo();
        String busqueda = null;

        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<EstudianteFila> page = repository.buscar(anioId, busqueda, gradoId, grupoId, activo, sinGrupo, pageRequest);

        List<EstudianteFilaResponse> contenido = new ArrayList<>();

        for (EstudianteFila fila : page.getContent()) {
            contenido.add(EstudianteFilaResponse.desde(fila));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteDetalleResponse buscar(String codigo) {

        TenantSupport.requireTenant(em);
        return armarDetalle(obtener(codigo));
    }

    // ---------------------------------------------------------------- crear y editar

    @Override
    @Transactional
    public EstudianteDetalleResponse crear(EstudianteRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);

        if (request.grupoId() == null) {
            throw ApiException.invalido("Selecciona el grupo del estudiante");
        }

        GrupoResponse grupo = estructuraService.buscarGrupo(request.grupoId());
        estructuraService.buscarAnioEditable(grupo.anioId());

        String documento = request.nroDoc().trim();

        if (repository.existsByNroDoc(documento)) {
            throw ApiException.conflicto("Ya existe un estudiante con el documento " + documento);
        }

        // si el documento ya es de un docente o admin, falla aqui con su propio mensaje
        Usuario usuario = usuarioService.crear(documento, Rol.ESTUDIANTE);

        Estudiante estudiante = new Estudiante();
        estudiante.setInstitucionId(institucionId);
        estudiante.setUsuario(usuario);
        estudiante.setCodigo(CodigoAleatorio.generar());
        estudiante.setCodigoQr(CodigoAleatorio.generar());
        copiarDatos(request, estudiante);
        repository.save(estudiante);

        matriculaService.ubicar(estudiante.getId(), grupo, null);

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse actualizar(String codigo, EstudianteRequest request) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        String documento = request.nroDoc().trim();

        if (!documento.equals(estudiante.getNroDoc())) {
            if (repository.existsByNroDocAndIdNot(documento, estudiante.getId())) {
                throw ApiException.conflicto("Ya existe un estudiante con el documento " + documento);
            }
            usuarioService.cambiarDocumento(estudiante.getUsuario().getId(), documento);
        }

        copiarDatos(request, estudiante);
        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse cambiarGrupo(String codigo, Long grupoId, String motivo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        GrupoResponse grupo = estructuraService.buscarGrupo(grupoId);

        boolean volvio = matriculaService.ubicar(estudiante.getId(), grupo, motivo);

        // estaba retirado: vuelve a poder entrar
        if (volvio && !estudiante.getUsuario().isActivo()) {
            usuarioService.cambiarEstadoPorAdmin(estudiante.getUsuario().getId(), true);
            bitacoraService.registrar("REINGRESO", "estudiante", estudiante.getId(), vacioANull(motivo));
        }

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse retirar(String codigo, String motivo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        int cerradas = matriculaService.retirar(estudiante.getId(), motivo);

        if (cerradas == 0) {
            throw ApiException.conflicto("El estudiante no tiene una matricula activa");
        }

        usuarioService.cambiarEstadoPorAdmin(estudiante.getUsuario().getId(), false);
        bitacoraService.registrar("RETIRO", "estudiante", estudiante.getId(), motivo.trim());

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse guardarFamiliares(String codigo, List<FamiliarRequest> familiares) {

        Long institucionId = TenantSupport.requireTenant(em);
        Estudiante estudiante = obtener(codigo);

        if (familiares.size() > 3) {
            throw ApiException.invalido("Maximo 3 familiares por estudiante");
        }

        for (FamiliarRequest familiar : familiares) {
            if (familiar.recibeSms() && vacio(familiar.celular())) {
                throw ApiException.invalido("Para que " + familiar.nombres().trim() + " reciba mensajes necesita celular");
            }
        }

        familiarRepository.borrarDelEstudiante(estudiante.getId());

        // borrarDelEstudiante limpia el contexto de jpa, el estudiante se vuelve a leer
        Estudiante actual = obtener(codigo);
        int posicion = 1;

        for (FamiliarRequest datos : familiares) {
            Familiar familiar = new Familiar();
            familiar.setInstitucionId(institucionId);
            familiar.setEstudiante(actual);
            familiar.setPosicion(posicion);
            familiar.setNombres(datos.nombres().trim());
            familiar.setApellidos(vacioANull(datos.apellidos()));
            familiar.setParentesco(datos.parentesco());
            familiar.setCelular(vacioANull(datos.celular()));
            familiar.setRecibeSms(datos.recibeSms());
            familiarRepository.save(familiar);
            posicion++;
        }

        return armarDetalle(actual);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse cambiarSmsFamiliares(String codigo, boolean activo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        estudiante.setSmsFamiliares(activo);

        // queda en la bitacora porque normalmente se apaga por una situacion delicada en la familia
        if (activo) {
            bitacoraService.registrar("ACTIVAR_SMS_FAMILIARES", "estudiante", estudiante.getId(), null);
        } else {
            bitacoraService.registrar("DESACTIVAR_SMS_FAMILIARES", "estudiante", estudiante.getId(), null);
        }

        return armarDetalle(estudiante);
    }

    // ---------------------------------------------------------------- usuario del estudiante

    @Override
    @Transactional
    public EstudianteDetalleResponse restablecerContrasena(String codigo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        usuarioService.restablecerContrasenaPorAdmin(estudiante.getUsuario().getId());

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse cambiarEstado(String codigo, boolean activo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        usuarioService.cambiarEstadoPorAdmin(estudiante.getUsuario().getId(), activo);

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public int cambiarEstadoMasivo(EstadoMasivoEstudiantesRequest request) {

        TenantSupport.requireTenant(em);
        boolean activo = request.activo();

        if (request.todos()) {
            return usuarioService.cambiarEstadoMasivo(Rol.ESTUDIANTE, null, activo);
        }

        List<Long> usuarioIds;

        if (request.codigos() != null && !request.codigos().isEmpty()) {
            usuarioIds = repository.usuarioIdsPorCodigos(request.codigos());
        } else if (request.grupoId() != null) {
            estructuraService.buscarGrupo(request.grupoId());
            usuarioIds = repository.usuarioIdsPorGrupo(request.grupoId());
        } else if (request.gradoId() != null) {
            usuarioIds = repository.usuarioIdsPorGrado(idAnioActivo(), request.gradoId());
        } else {
            throw ApiException.invalido("Selecciona estudiantes, un grupo, un grado o todos");
        }

        if (usuarioIds.isEmpty()) {
            return 0;
        }

        return usuarioService.cambiarEstadoMasivo(null, usuarioIds, activo);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarSinGrupo(Long anioId) {

        TenantSupport.requireTenant(em);
        estructuraService.buscarAnioEditable(anioId);

        return repository.sinMatricula(anioId).size();
    }

    @Override
    @Transactional
    public int inactivarSinGrupo(Long anioId) {

        TenantSupport.requireTenant(em);
        AnioLectivoResponse anio = estructuraService.buscarAnioEditable(anioId);

        List<Long> estudianteIds = new ArrayList<>();
        List<Long> usuarioIds = new ArrayList<>();

        for (Object[] fila : repository.sinMatricula(anioId)) {
            estudianteIds.add(((Number) fila[0]).longValue());
            usuarioIds.add(((Number) fila[1]).longValue());
        }

        if (usuarioIds.isEmpty()) {
            return 0;
        }

        // la matricula que les quedo abierta del anio anterior se cierra; la de los graduados ya esta cerrada
        matriculaService.cerrarActivas(estudianteIds, "No continuo en " + anio.anio());

        return usuarioService.cambiarEstadoMasivo(null, usuarioIds, false);
    }

    // ---------------------------------------------------------------- qr

    @Override
    @Transactional(readOnly = true)
    public QrResponse verQr(String codigo) {

        TenantSupport.requireTenant(em);
        return armarQr(obtener(codigo));
    }

    @Override
    @Transactional
    public QrResponse regenerarQr(String codigo) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        estudiante.setCodigoQr(CodigoAleatorio.generar());
        bitacoraService.registrar("REGENERAR_QR", "estudiante", estudiante.getId(), null);

        return armarQr(estudiante);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QrResponse> qrDelGrupo(Long grupoId) {

        TenantSupport.requireTenant(em);

        GrupoResponse grupo = estructuraService.buscarGrupo(grupoId);
        List<QrResponse> respuesta = new ArrayList<>();

        for (Estudiante estudiante : repository.buscarPorGrupo(grupoId)) {
            respuesta.add(new QrResponse(
                    estudiante.getNombres(),
                    estudiante.getApellidos(),
                    grupo.gradoNombre(),
                    grupo.nombre(),
                    enlaceQr(estudiante)));
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteFilaResponse buscarPorQr(String codigoQr) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = repository.buscarPorCodigoQr(codigoQr);

        if (estudiante == null) {
            throw ApiException.noEncontrado("Este QR no es valido o fue reemplazado por uno nuevo");
        }

        MatriculaResponse actual = matriculaActual(matriculaService.trayectoria(estudiante.getId()));
        String grado = null;
        String grupo = null;

        if (actual != null) {
            grado = actual.gradoNombre();
            grupo = actual.grupoNombre();
        }

        return new EstudianteFilaResponse(
                estudiante.getCodigo(),
                estudiante.getTipoDoc(),
                estudiante.getNroDoc(),
                estudiante.getNombres(),
                estudiante.getApellidos(),
                estudiante.getUsuario().isActivo(),
                grado,
                grupo);
    }

    // ---------------------------------------------------------------- para otros modulos

    @Override
    @Transactional(readOnly = true)
    public NombrePersona buscarNombre(Long usuarioId) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = repository.buscarPorUsuario(usuarioId);

        if (estudiante == null) {
            return null;
        }

        return new NombrePersona(estudiante.getNombres(), estudiante.getApellidos());
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteBasico basicoPorCodigo(String codigo) {

        TenantSupport.requireTenant(em);
        return basico(obtener(codigo));
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteBasico basicoPorUsuario(Long usuarioId) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = repository.buscarPorUsuario(usuarioId);

        if (estudiante == null) {
            return null;
        }

        return basico(estudiante);
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteBasico basicoPorId(Long estudianteId) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = repository.findById(estudianteId).orElse(null);

        if (estudiante == null) {
            throw ApiException.noEncontrado("El estudiante no existe");
        }

        return basico(estudiante);
    }

    private EstudianteBasico basico(Estudiante estudiante) {

        return new EstudianteBasico(
                estudiante.getId(),
                estudiante.getCodigo(),
                estudiante.getNombres(),
                estudiante.getApellidos(),
                estudiante.getUsuario().isActivo());
    }

    // ---------------------------------------------------------------- ayudas

    private Estudiante obtener(String codigo) {

        Estudiante estudiante = repository.buscarPorCodigo(codigo);

        if (estudiante == null) {
            throw ApiException.noEncontrado("El estudiante no existe");
        }

        return estudiante;
    }

    private void copiarDatos(EstudianteRequest request, Estudiante estudiante) {

        estudiante.setTipoDoc(request.tipoDoc());
        estudiante.setNroDoc(request.nroDoc().trim());
        estudiante.setNombres(request.nombres().trim());
        estudiante.setApellidos(request.apellidos().trim());
        estudiante.setGenero(vacioANull(request.genero()));
        estudiante.setFechaNacimiento(request.fechaNacimiento());
        estudiante.setCelular(vacioANull(request.celular()));
        estudiante.setCorreo(vacioANull(request.correo()));
        estudiante.setDireccion(vacioANull(request.direccion()));
        estudiante.setBarrio(vacioANull(request.barrio()));
        estudiante.setEps(vacioANull(request.eps()));
        estudiante.setRh(vacioANull(request.rh()));
        estudiante.setCondicionesSalud(vacioANull(request.condicionesSalud()));
    }

    private EstudianteDetalleResponse armarDetalle(Estudiante estudiante) {

        List<MatriculaResponse> trayectoria = matriculaService.trayectoria(estudiante.getId());

        List<FamiliarResponse> familiares = new ArrayList<>();

        for (Familiar familiar : familiarRepository.findByEstudianteIdOrderByPosicionAsc(estudiante.getId())) {
            familiares.add(FamiliarResponse.desde(familiar));
        }

        Usuario usuario = estudiante.getUsuario();

        return new EstudianteDetalleResponse(
                estudiante.getCodigo(),
                estudiante.getTipoDoc(),
                estudiante.getNroDoc(),
                estudiante.getNombres(),
                estudiante.getApellidos(),
                estudiante.getGenero(),
                estudiante.getFechaNacimiento(),
                estudiante.getCelular(),
                estudiante.getCorreo(),
                estudiante.getDireccion(),
                estudiante.getBarrio(),
                estudiante.getEps(),
                estudiante.getRh(),
                estudiante.getCondicionesSalud(),
                estudiante.isSmsFamiliares(),
                usuario.isActivo(),
                usuario.isDebeCambiarContrasena(),
                matriculaActual(trayectoria),
                trayectoria,
                familiares);
    }

    // la del anio activo, null si no tiene
    private MatriculaResponse matriculaActual(List<MatriculaResponse> trayectoria) {

        for (MatriculaResponse matricula : trayectoria) {
            if (matricula.anioActivo()) {
                return matricula;
            }
        }

        return null;
    }

    private QrResponse armarQr(Estudiante estudiante) {

        MatriculaResponse actual = matriculaActual(matriculaService.trayectoria(estudiante.getId()));
        String grado = null;
        String grupo = null;

        if (actual != null) {
            grado = actual.gradoNombre();
            grupo = actual.grupoNombre();
        }

        return new QrResponse(estudiante.getNombres(), estudiante.getApellidos(), grado, grupo, enlaceQr(estudiante));
    }

    // el qr solo lleva el enlace con el codigo del qr, ningun dato del estudiante
    private String enlaceQr(Estudiante estudiante) {
        return urlFront + "/" + TenantContext.getSlug() + "/q/" + estudiante.getCodigoQr();
    }

    private Long idAnioActivo() {

        AnioLectivoResponse activo = estructuraService.anioActivo();

        if (activo == null) {
            return null;
        }

        return activo.id();
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

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private String vacioANull(String texto) {

        if (vacio(texto)) {
            return null;
        }

        return texto.trim();
    }
}
