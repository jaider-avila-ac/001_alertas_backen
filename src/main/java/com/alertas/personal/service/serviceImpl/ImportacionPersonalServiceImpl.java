package com.alertas.personal.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.personal.dto.FilaPersonalImportada;
import com.alertas.personal.dto.ImportacionPersonalGuardada;
import com.alertas.personal.dto.ResultadoImportacionPersonalResponse;
import com.alertas.personal.dto.VistaPreviaPersonalResponse;
import com.alertas.personal.excel.ExcelPersonal;
import com.alertas.personal.model.Personal;
import com.alertas.personal.repository.PersonalFila;
import com.alertas.personal.repository.PersonalRepository;
import com.alertas.personal.service.ImportacionPersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.ErrorFila;
import com.alertas.shared.excel.ArchivoExcel;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportacionPersonalServiceImpl implements ImportacionPersonalService {

    private static final int MAXIMO_FILAS = 1000;
    private static final int MAXIMO_ERRORES_MOSTRADOS = 100;
    private static final Duration DURACION_VISTA_PREVIA = Duration.ofMinutes(15);

    private static final Set<String> TIPOS_DOC = Set.of("RC", "TI", "CC", "CE", "PPT");
    private static final Pattern DOCUMENTO = Pattern.compile("[A-Za-z0-9]{3,20}");
    private static final Pattern CELULAR = Pattern.compile("3[0-9]{9}");
    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    // formas en que la gente escribe el rol
    private static final Map<String, String> ROLES = Map.of(
            "DOCENTE", "DOCENTE",
            "PROFESOR", "DOCENTE",
            "PSICORIENTADOR", "PSICORIENTADOR",
            "PSICOORIENTADOR", "PSICORIENTADOR",
            "ORIENTADOR", "PSICORIENTADOR");

    private final PersonalRepository personalRepository;
    private final UsuarioService usuarioService;
    private final BitacoraService bitacoraService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public ImportacionPersonalServiceImpl(
            PersonalRepository personalRepository,
            UsuarioService usuarioService,
            BitacoraService bitacoraService,
            StringRedisTemplate redis,
            ObjectMapper mapper,
            EntityManager em) {

        this.personalRepository = personalRepository;
        this.usuarioService = usuarioService;
        this.bitacoraService = bitacoraService;
        this.redis = redis;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public byte[] plantilla() {
        return ExcelPersonal.plantilla();
    }

    // ---------------------------------------------------------------- vista previa

    @Override
    @Transactional(readOnly = true)
    public VistaPreviaPersonalResponse vistaPrevia(InputStream archivo) {

        Long institucionId = TenantSupport.requireTenant(em);
        List<Map<String, String>> filas = ExcelPersonal.leer(archivo, MAXIMO_FILAS);

        if (filas.isEmpty()) {
            throw ApiException.invalido("El archivo no tiene personas");
        }

        List<ErrorFila> errores = new ArrayList<>();
        List<FilaPersonalImportada> validas = new ArrayList<>();
        Set<String> documentosVistos = new HashSet<>();

        for (Map<String, String> datos : filas) {
            int numero = Integer.parseInt(datos.get(ArchivoExcel.NUMERO_FILA));
            List<String> problemas = new ArrayList<>();
            FilaPersonalImportada fila = validarFila(numero, datos, problemas);

            if (fila != null && !documentosVistos.add(fila.nroDoc())) {
                problemas.add("el documento " + fila.nroDoc() + " esta repetido en el archivo");
            }

            if (!problemas.isEmpty()) {
                errores.add(new ErrorFila(numero, String.join("; ", problemas)));
                continue;
            }

            validas.add(fila);
        }

        // quienes ya estan en el personal se actualizan; un administrador no se toca por aqui
        Set<String> documentos = new HashSet<>();
        for (FilaPersonalImportada fila : validas) {
            documentos.add(fila.nroDoc());
        }

        Map<String, Personal> existentes = new HashMap<>();
        for (Personal personal : personalRepository.buscarPorDocumentos(documentos)) {
            existentes.put(personal.getNroDoc(), personal);
        }

        Set<String> nuevos = new HashSet<>(documentos);
        nuevos.removeAll(existentes.keySet());

        // un documento nuevo que ya tiene usuario es de un estudiante: no se puede usar
        Set<String> usadosPorOtros = usuarioService.documentosEnUso(nuevos);
        int actualizados = 0;

        for (FilaPersonalImportada fila : validas) {
            Personal existente = existentes.get(fila.nroDoc());

            if (existente != null && existente.getUsuario().getRol() == Rol.ADMIN) {
                errores.add(new ErrorFila(fila.fila(), "el documento " + fila.nroDoc()
                        + " es de un administrador, sus datos los cambia el superadmin"));
            } else if (existente != null && !existente.getUsuario().getRol().name().equals(fila.rol())) {
                errores.add(new ErrorFila(fila.fila(), "el documento " + fila.nroDoc() + " ya esta registrado como "
                        + nombreRol(existente.getUsuario().getRol()) + ", el rol no se puede cambiar"));
            } else if (usadosPorOtros.contains(fila.nroDoc())) {
                errores.add(new ErrorFila(fila.fila(), "el documento " + fila.nroDoc() + " ya lo usa un estudiante"));
            } else if (existente != null) {
                actualizados++;
            }
        }

        errores.sort((a, b) -> Integer.compare(a.fila(), b.fila()));

        String token = null;

        if (errores.isEmpty()) {
            token = UUID.randomUUID().toString();
            guardar(institucionId, token, new ImportacionPersonalGuardada(validas));
        }

        int totalErrores = errores.size();
        List<ErrorFila> mostrados = errores;
        if (errores.size() > MAXIMO_ERRORES_MOSTRADOS) {
            mostrados = errores.subList(0, MAXIMO_ERRORES_MOSTRADOS);
        }

        int nuevosValidos = validas.size() - actualizados;
        if (!errores.isEmpty()) {
            nuevosValidos = Math.max(0, nuevos.size() - usadosPorOtros.size());
        }

        return new VistaPreviaPersonalResponse(token, filas.size(), nuevosValidos, actualizados, mostrados, totalErrores);
    }

    // devuelve null si la fila tiene errores (quedan en problemas)
    private FilaPersonalImportada validarFila(int numero, Map<String, String> datos, List<String> problemas) {

        String tipoDoc = datos.getOrDefault(ExcelPersonal.TIPO_DOC, "").toUpperCase();
        String nroDoc = datos.getOrDefault(ExcelPersonal.NUMERO_DOCUMENTO, "").replace(".", "").replace(" ", "");
        String nombres = datos.getOrDefault(ExcelPersonal.NOMBRES, "");
        String apellidos = datos.getOrDefault(ExcelPersonal.APELLIDOS, "");
        String rolTexto = datos.getOrDefault(ExcelPersonal.ROL, "").toUpperCase().replace(" ", "");
        String correo = datos.getOrDefault(ExcelPersonal.CORREO, "");
        String celular = datos.getOrDefault(ExcelPersonal.CELULAR, "").replace(" ", "");

        if (!TIPOS_DOC.contains(tipoDoc)) {
            problemas.add("tipo de documento no valido (CC, CE, PPT, TI o RC)");
        }
        if (!DOCUMENTO.matcher(nroDoc).matches()) {
            problemas.add("numero de documento no valido");
        }
        if (nombres.isEmpty() || nombres.length() > 80) {
            problemas.add("faltan los nombres");
        }
        if (apellidos.isEmpty() || apellidos.length() > 80) {
            problemas.add("faltan los apellidos");
        }

        String rol = ROLES.get(rolTexto);
        if (rol == null) {
            problemas.add("rol no valido (DOCENTE o PSICORIENTADOR)");
        }

        if (!correo.isEmpty() && (correo.length() > 120 || !CORREO.matcher(correo).matches())) {
            problemas.add("correo no valido");
        }
        if (!celular.isEmpty() && !CELULAR.matcher(celular).matches()) {
            problemas.add("celular no valido (10 digitos, empieza por 3)");
        }

        if (!problemas.isEmpty()) {
            return null;
        }

        return new FilaPersonalImportada(
                numero, tipoDoc, nroDoc, nombres, apellidos, rol, vacioANull(correo), vacioANull(celular));
    }

    // ---------------------------------------------------------------- confirmar

    @Override
    @Transactional
    public ResultadoImportacionPersonalResponse confirmar(String token) {

        Long institucionId = TenantSupport.requireTenant(em);
        ImportacionPersonalGuardada guardada = sacar(institucionId, token);

        List<String> documentos = new ArrayList<>();
        for (FilaPersonalImportada fila : guardada.filas()) {
            documentos.add(fila.nroDoc());
        }

        Map<String, Personal> existentes = new HashMap<>();
        for (Personal personal : personalRepository.buscarPorDocumentos(documentos)) {
            existentes.put(personal.getNroDoc(), personal);
        }

        // los nuevos se crean agrupados por rol (las contrasenas se cifran en paralelo)
        List<String> docentesNuevos = new ArrayList<>();
        List<String> psicorientadoresNuevos = new ArrayList<>();

        for (FilaPersonalImportada fila : guardada.filas()) {
            if (existentes.containsKey(fila.nroDoc())) {
                continue;
            }
            if (fila.rol().equals("DOCENTE")) {
                docentesNuevos.add(fila.nroDoc());
            } else {
                psicorientadoresNuevos.add(fila.nroDoc());
            }
        }

        Map<String, Usuario> usuariosNuevos = new HashMap<>();
        usuariosNuevos.putAll(usuarioService.crearVarios(docentesNuevos, Rol.DOCENTE));
        usuariosNuevos.putAll(usuarioService.crearVarios(psicorientadoresNuevos, Rol.PSICORIENTADOR));

        int creados = 0;
        int actualizados = 0;

        for (FilaPersonalImportada fila : guardada.filas()) {
            Personal personal = existentes.get(fila.nroDoc());

            if (personal == null) {
                personal = new Personal();
                personal.setInstitucionId(institucionId);
                personal.setCodigo(CodigoAleatorio.generar());
                personal.setUsuario(usuariosNuevos.get(fila.nroDoc()));
                creados++;
            } else {
                // el rol no cambia: se reviso en la vista previa y aqui se vuelve a mirar por si acaso
                if (!personal.getUsuario().getRol().name().equals(fila.rol())) {
                    throw ApiException.conflicto("El rol de " + fila.nroDoc() + " no se puede cambiar. Sube el archivo otra vez");
                }
                actualizados++;
            }

            personal.setTipoDoc(fila.tipoDoc());
            personal.setNroDoc(fila.nroDoc());
            personal.setNombres(fila.nombres());
            personal.setApellidos(fila.apellidos());
            if (fila.correo() != null) {
                personal.setCorreo(fila.correo());
            }
            if (fila.celular() != null) {
                personal.setCelular(fila.celular());
            }
            personalRepository.save(personal);
        }

        bitacoraService.registrar("IMPORTAR_PERSONAL", "personal", null,
                creados + " creados, " + actualizados + " actualizados");

        return new ResultadoImportacionPersonalResponse(creados, actualizados);
    }

    // ---------------------------------------------------------------- exportar

    @Override
    @Transactional(readOnly = true)
    public byte[] exportar(String texto, String rol, Boolean activo) {

        TenantSupport.requireTenant(em);

        String busqueda = null;
        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        Page<PersonalFila> page = personalRepository.buscar(busqueda, rol, activo, Pageable.unpaged());

        String[] columnas = {
                ExcelPersonal.TIPO_DOC, ExcelPersonal.NUMERO_DOCUMENTO, ExcelPersonal.NOMBRES,
                ExcelPersonal.APELLIDOS, ExcelPersonal.ROL, ExcelPersonal.CORREO, "ESTADO"
        };

        List<String[]> filas = new ArrayList<>();
        for (PersonalFila fila : page.getContent()) {
            String estado = "Inactivo";
            if (Boolean.TRUE.equals(fila.getActivo())) {
                estado = "Activo";
            }
            filas.add(new String[] {
                    fila.getTipoDoc(), fila.getNroDoc(), fila.getNombres(), fila.getApellidos(),
                    fila.getRol(), fila.getCorreo(), estado
            });
        }

        return ExcelPersonal.exportar(columnas, filas);
    }

    // ---------------------------------------------------------------- ayudas

    private void guardar(Long institucionId, String token, ImportacionPersonalGuardada datos) {

        try {
            String json = mapper.writeValueAsString(datos);
            redis.opsForValue().set(clave(institucionId, token), json, DURACION_VISTA_PREVIA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la vista previa", e);
        }
    }

    // se lee y se borra: una vista previa solo se puede confirmar una vez
    private ImportacionPersonalGuardada sacar(Long institucionId, String token) {

        String json = redis.opsForValue().getAndDelete(clave(institucionId, token));

        if (json == null) {
            throw ApiException.noEncontrado("La vista previa vencio o ya se confirmo. Sube el archivo otra vez");
        }

        try {
            return mapper.readValue(json, ImportacionPersonalGuardada.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la vista previa", e);
        }
    }

    // lleva la institucion: el token de un colegio no sirve en otro
    private String clave(Long institucionId, String token) {
        return "import:per:" + institucionId + ":" + token;
    }

    private String nombreRol(Rol rol) {

        if (rol == Rol.PSICORIENTADOR) {
            return "psicorientador";
        }

        return "docente";
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
