package com.alertas.estudiante.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.bitacora.service.BitacoraService;
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
import com.alertas.estudiante.service.FechaNacimiento;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.RangoEdad;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.shared.validacion.Formato;
import com.alertas.shared.interceptor.TenantContext;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
    private final FamiliarRepository familiarRepository;
    private final UsuarioService usuarioService;
    private final BitacoraService bitacoraService;
    private final EntityManager em;
    private final String urlFront;

    public EstudianteServiceImpl(
            EstudianteRepository repository,
            FamiliarRepository familiarRepository,
            UsuarioService usuarioService,
            BitacoraService bitacoraService,
            EntityManager em,
            @Value("${app.url-front}") String urlFront) {

        this.repository = repository;
        this.familiarRepository = familiarRepository;
        this.usuarioService = usuarioService;
        this.bitacoraService = bitacoraService;
        this.em = em;
        this.urlFront = urlFront;
    }

    // ---------------------------------------------------------------- consultar

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EstudianteFilaResponse> listar(
            String texto, String grupo, String edad, Boolean activo, int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        String busqueda = null;

        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<EstudianteFila> page = repository.buscar(busqueda, Formato.espacios(grupo), RangoEdad.validar(edad), activo, pageRequest);

        List<EstudianteFilaResponse> contenido = new ArrayList<>();

        for (EstudianteFila fila : page.getContent()) {
            contenido.add(EstudianteFilaResponse.desde(fila));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> grupos() {

        TenantSupport.requireTenant(em);
        return repository.grupos();
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

        String documento = Formato.documento(request.nroDoc());

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

        return armarDetalle(estudiante);
    }

    @Override
    @Transactional
    public EstudianteDetalleResponse actualizar(String codigo, EstudianteRequest request) {

        TenantSupport.requireTenant(em);

        Estudiante estudiante = obtener(codigo);
        String documento = Formato.documento(request.nroDoc());

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
            familiar.setNombres(Formato.nombrePropio(datos.nombres()));
            familiar.setApellidos(Formato.nombrePropio(datos.apellidos()));
            familiar.setParentesco(datos.parentesco());
            familiar.setCelular(Formato.telefono(datos.celular()));
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
        } else if (!vacio(request.grupo())) {
            usuarioIds = repository.usuarioIdsPorGrupo(Formato.espacios(request.grupo()));
        } else if (RangoEdad.validar(request.edad()) != null) {
            usuarioIds = repository.usuarioIdsPorEdad(request.edad());
        } else {
            throw ApiException.invalido("Selecciona estudiantes, un grupo, un rango de edad o todos");
        }

        if (usuarioIds.isEmpty()) {
            return 0;
        }

        return usuarioService.cambiarEstadoMasivo(null, usuarioIds, activo);
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
    public List<QrResponse> qrDelGrupo(String grupo) {

        TenantSupport.requireTenant(em);

        List<QrResponse> respuesta = new ArrayList<>();

        for (Estudiante estudiante : repository.activosDelGrupo(Formato.espacios(grupo))) {
            respuesta.add(armarQr(estudiante));
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

return new EstudianteFilaResponse(
                estudiante.getCodigo(),
                estudiante.getTipoDoc(),
                estudiante.getNroDoc(),
                estudiante.getNombres(),
                estudiante.getApellidos(),
                estudiante.getUsuario().isActivo(),
                estudiante.getGrupo(),
                estudiante.getFechaNacimiento());
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
                estudiante.getGrupo(),
                estudiante.getUsuario().isActivo(),
                estudiante.getUsuario().getId());
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

        String problemaFecha = FechaNacimiento.problema(request.fechaNacimiento(), LocalDate.now());
        if (problemaFecha != null) {
            throw ApiException.invalido(problemaFecha);
        }

        estudiante.setTipoDoc(request.tipoDoc());
        estudiante.setNroDoc(Formato.documento(request.nroDoc()));
        estudiante.setNombres(Formato.nombrePropio(request.nombres()));
        estudiante.setApellidos(Formato.nombrePropio(request.apellidos()));
        estudiante.setGrupo(Formato.espacios(request.grupo()));
        estudiante.setGenero(vacioANull(request.genero()));
        estudiante.setFechaNacimiento(request.fechaNacimiento());
        estudiante.setCelular(Formato.telefono(request.celular()));
        estudiante.setCorreo(Formato.correo(request.correo()));
        estudiante.setDireccion(Formato.espacios(request.direccion()));
        estudiante.setBarrio(Formato.nombrePropio(request.barrio()));
        estudiante.setEps(Formato.espacios(request.eps()));
        estudiante.setRh(vacioANull(request.rh()));
        estudiante.setCondicionesSalud(Formato.texto(request.condicionesSalud()));
    }

    private EstudianteDetalleResponse armarDetalle(Estudiante estudiante) {

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
                estudiante.getGrupo(),
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
                familiares);
    }

    private QrResponse armarQr(Estudiante estudiante) {

        return new QrResponse(estudiante.getNombres(), estudiante.getApellidos(), estudiante.getGrupo(), enlaceQr(estudiante));
    }

    // el qr solo lleva el enlace con el codigo del qr, ningun dato del estudiante
    private String enlaceQr(Estudiante estudiante) {
        return urlFront + "/" + TenantContext.getSlug() + "/q/" + estudiante.getCodigoQr();
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
