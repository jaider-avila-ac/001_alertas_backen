package com.alertas.estudiante.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GradoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.shared.dto.ErrorFila;
import com.alertas.estudiante.dto.FilaImportada;
import com.alertas.estudiante.dto.ImportacionGuardada;
import com.alertas.estudiante.dto.ResultadoImportacionResponse;
import com.alertas.estudiante.dto.VistaPreviaImportacionResponse;
import com.alertas.estudiante.excel.ExcelEstudiantes;
import com.alertas.estudiante.model.Estudiante;
import com.alertas.estudiante.model.Familiar;
import com.alertas.estudiante.repository.EstudianteFila;
import com.alertas.estudiante.repository.EstudianteRepository;
import com.alertas.estudiante.repository.FamiliarRepository;
import com.alertas.estudiante.service.FechaNacimiento;
import com.alertas.estudiante.service.ImportacionEstudiantesService;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
public class ImportacionEstudiantesServiceImpl implements ImportacionEstudiantesService {

    private static final int MAXIMO_FILAS = 3000;
    // al usuario se le muestran los primeros, el total va aparte
    private static final int MAXIMO_ERRORES_MOSTRADOS = 100;
    private static final Duration DURACION_VISTA_PREVIA = Duration.ofMinutes(15);

    private static final Set<String> TIPOS_DOC = Set.of("RC", "TI", "CC", "CE", "PPT");
    private static final Set<String> PARENTESCOS =
            Set.of("MADRE", "PADRE", "ACUDIENTE", "ABUELO", "HERMANO", "TIO", "OTRO");
    private static final Pattern DOCUMENTO = Pattern.compile("[A-Za-z0-9]{3,20}");
    private static final Pattern CELULAR = Pattern.compile("3[0-9]{9}");
    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Set<String> RH = Set.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-");

    // nombres de grado que la gente escribe distinto al catalogo
    private static final Map<String, Integer> OTROS_NOMBRES_GRADO = Map.of(
            "undecimo", 11,
            "onceavo", 11,
            "grado cero", 0,
            "preescolar", 0);

    private final EstudianteRepository estudianteRepository;
    private final MatriculaService matriculaService;
    private final FamiliarRepository familiarRepository;
    private final UsuarioService usuarioService;
    private final EstructuraService estructuraService;
    private final BitacoraService bitacoraService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public ImportacionEstudiantesServiceImpl(
            EstudianteRepository estudianteRepository,
            MatriculaService matriculaService,
            FamiliarRepository familiarRepository,
            UsuarioService usuarioService,
            EstructuraService estructuraService,
            BitacoraService bitacoraService,
            StringRedisTemplate redis,
            ObjectMapper mapper,
            EntityManager em) {

        this.estudianteRepository = estudianteRepository;
        this.matriculaService = matriculaService;
        this.familiarRepository = familiarRepository;
        this.usuarioService = usuarioService;
        this.estructuraService = estructuraService;
        this.bitacoraService = bitacoraService;
        this.redis = redis;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public byte[] plantilla() {
        return ExcelEstudiantes.plantilla();
    }

    // ---------------------------------------------------------------- vista previa

    @Override
    @Transactional(readOnly = true)
    public VistaPreviaImportacionResponse vistaPrevia(InputStream archivo, Long anioId) {

        Long institucionId = TenantSupport.requireTenant(em);
        AnioLectivoResponse anio = anioDestino(anioId);

        List<Map<String, String>> filas = ExcelEstudiantes.leer(archivo, MAXIMO_FILAS);

        if (filas.isEmpty()) {
            throw ApiException.invalido("El archivo no tiene estudiantes");
        }

        Map<String, GradoResponse> grados = mapaDeGrados();
        Set<String> gruposExistentes = new HashSet<>();

        for (GrupoResponse grupo : estructuraService.listarGrupos(anio.id())) {
            gruposExistentes.add(claveGrupo(grupo.gradoId(), grupo.nombre()));
        }

        List<ErrorFila> errores = new ArrayList<>();
        List<FilaImportada> validas = new ArrayList<>();
        Set<String> documentosVistos = new HashSet<>();
        Set<String> gruposACrear = new LinkedHashSet<>();

        for (Map<String, String> datos : filas) {
            int numero = Integer.parseInt(datos.get("_fila"));
            List<String> problemas = new ArrayList<>();
            FilaImportada fila = validarFila(numero, datos, grados, problemas);

            if (fila != null && !documentosVistos.add(fila.nroDoc())) {
                problemas.add("el documento " + fila.nroDoc() + " esta repetido en el archivo");
            }

            if (!problemas.isEmpty()) {
                errores.add(new ErrorFila(numero, String.join("; ", problemas)));
                continue;
            }

            validas.add(fila);

            String clave = claveGrupo(fila.gradoId(), fila.grupoNombre());
            if (!gruposExistentes.contains(clave)) {
                gruposACrear.add(fila.gradoNombre() + " " + fila.grupoNombre().toUpperCase());
                gruposExistentes.add(clave);
            }
        }

        // quienes ya son estudiantes se actualizan; los demas se crean
        Set<String> documentos = new HashSet<>();
        for (FilaImportada fila : validas) {
            documentos.add(fila.nroDoc());
        }

        Set<String> yaEstudiantes = new HashSet<>();
        for (Estudiante estudiante : estudianteRepository.buscarPorDocumentos(documentos)) {
            yaEstudiantes.add(estudiante.getNroDoc());
        }

        // un documento nuevo que ya tiene usuario es de un docente o admin: no se puede usar
        Set<String> nuevos = new HashSet<>(documentos);
        nuevos.removeAll(yaEstudiantes);
        Set<String> usadosPorOtros = usuarioService.documentosEnUso(nuevos);

        for (FilaImportada fila : validas) {
            if (usadosPorOtros.contains(fila.nroDoc())) {
                errores.add(new ErrorFila(fila.fila(),
                        "el documento " + fila.nroDoc() + " ya lo usa un docente, psicorientador o administrador"));
            }
        }

        errores.sort((a, b) -> Integer.compare(a.fila(), b.fila()));

        String token = null;

        if (errores.isEmpty()) {
            token = UUID.randomUUID().toString();
            guardar(institucionId, token, new ImportacionGuardada(anio.id(), validas));
        }

        int totalErrores = errores.size();
        List<ErrorFila> mostrados = errores;
        if (errores.size() > MAXIMO_ERRORES_MOSTRADOS) {
            mostrados = errores.subList(0, MAXIMO_ERRORES_MOSTRADOS);
        }

        int actualizados = yaEstudiantes.size();

        return new VistaPreviaImportacionResponse(
                token,
                anio.anio(),
                filas.size(),
                validas.size() - actualizados - usadosPorOtros.size(),
                actualizados,
                new ArrayList<>(gruposACrear),
                mostrados,
                totalErrores);
    }

    // devuelve null si la fila tiene errores (quedan en problemas)
    private FilaImportada validarFila(
            int numero, Map<String, String> datos, Map<String, GradoResponse> grados, List<String> problemas) {

        String tipoDoc = datos.getOrDefault(ExcelEstudiantes.TIPO_DOC, "").toUpperCase();
        String nroDoc = datos.getOrDefault(ExcelEstudiantes.NUMERO_DOCUMENTO, "").replace(".", "").replace(" ", "");
        String nombres = datos.getOrDefault(ExcelEstudiantes.NOMBRES, "");
        String apellidos = datos.getOrDefault(ExcelEstudiantes.APELLIDOS, "");
        String genero = datos.getOrDefault(ExcelEstudiantes.GENERO, "").toUpperCase();
        String fechaTexto = datos.getOrDefault(ExcelEstudiantes.FECHA_NACIMIENTO, "");
        String celular = datos.getOrDefault(ExcelEstudiantes.CELULAR, "").replace(" ", "");
        String gradoTexto = datos.getOrDefault(ExcelEstudiantes.GRADO, "");
        String grupo = datos.getOrDefault(ExcelEstudiantes.GRUPO, "");
        String famNombre = datos.getOrDefault(ExcelEstudiantes.FAMILIAR_NOMBRE, "");
        String famParentesco = datos.getOrDefault(ExcelEstudiantes.FAMILIAR_PARENTESCO, "").toUpperCase();
        String famCelular = datos.getOrDefault(ExcelEstudiantes.FAMILIAR_CELULAR, "").replace(" ", "");
        String correo = datos.getOrDefault(ExcelEstudiantes.CORREO, "");
        String direccion = datos.getOrDefault(ExcelEstudiantes.DIRECCION, "");
        String barrio = datos.getOrDefault(ExcelEstudiantes.BARRIO, "");
        String eps = datos.getOrDefault(ExcelEstudiantes.EPS, "");
        String rh = datos.getOrDefault(ExcelEstudiantes.RH, "").toUpperCase().replace(" ", "");
        String salud = datos.getOrDefault(ExcelEstudiantes.CONDICIONES_SALUD, "");

        if (!TIPOS_DOC.contains(tipoDoc)) {
            problemas.add("tipo de documento no valido (RC, TI, CC, CE o PPT)");
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
        if (!genero.isEmpty() && !Set.of("F", "M", "O").contains(genero)) {
            problemas.add("genero no valido (F, M u O)");
        }

        String fecha = null;
        if (!fechaTexto.isEmpty()) {
            fecha = leerFecha(fechaTexto);
            if (fecha == null) {
                problemas.add("fecha de nacimiento no valida (dd/mm/aaaa)");
            } else {
                String problemaFecha = FechaNacimiento.problema(LocalDate.parse(fecha), LocalDate.now());
                if (problemaFecha != null) {
                    problemas.add(problemaFecha.toLowerCase());
                }
            }
        }

        if (!celular.isEmpty() && !CELULAR.matcher(celular).matches()) {
            problemas.add("celular no valido (10 digitos, empieza por 3)");
        }

        GradoResponse grado = grados.get(normalizar(gradoTexto));
        if (grado == null) {
            problemas.add("grado '" + gradoTexto + "' no existe");
        } else if (!grado.activo()) {
            problemas.add("el grado " + grado.nombre() + " no lo ofrece la institucion (activalo en Grados y grupos)");
        }

        if (grupo.isEmpty() || grupo.length() > 20) {
            problemas.add("falta el grupo");
        }

        if (!famNombre.isEmpty()) {
            if (!PARENTESCOS.contains(famParentesco)) {
                problemas.add("parentesco del familiar no valido");
            }
            if (!famCelular.isEmpty() && !CELULAR.matcher(famCelular).matches()) {
                problemas.add("celular del familiar no valido");
            }
        }

        if (!correo.isEmpty() && (correo.length() > 120 || !CORREO.matcher(correo).matches())) {
            problemas.add("correo no valido");
        }
        if (direccion.length() > 150) {
            problemas.add("la direccion es muy larga");
        }
        if (barrio.length() > 80) {
            problemas.add("el barrio es muy largo");
        }
        if (eps.length() > 80) {
            problemas.add("la EPS es muy larga");
        }
        if (!rh.isEmpty() && !RH.contains(rh)) {
            problemas.add("RH no valido (O+, O-, A+, A-, B+, B-, AB+ o AB-)");
        }
        if (salud.length() > 500) {
            problemas.add("las condiciones de salud son muy largas (maximo 500 letras)");
        }

        if (!problemas.isEmpty()) {
            return null;
        }

        return new FilaImportada(
                numero,
                tipoDoc,
                nroDoc,
                nombres,
                apellidos,
                vacioANull(genero),
                fecha,
                vacioANull(celular),
                grado.id(),
                grado.nombre(),
                grupo,
                vacioANull(famNombre),
                vacioANull(famParentesco),
                vacioANull(famCelular),
                vacioANull(correo),
                vacioANull(direccion),
                vacioANull(barrio),
                vacioANull(eps),
                vacioANull(rh),
                vacioANull(salud));
    }

    // ---------------------------------------------------------------- confirmar

    @Override
    @Transactional
    public ResultadoImportacionResponse confirmar(String token) {

        Long institucionId = TenantSupport.requireTenant(em);
        ImportacionGuardada guardada = sacar(institucionId, token);

        // se vuelve a revisar: el anio pudo haber pasado mientras tanto
        AnioLectivoResponse anio = estructuraService.buscarAnioEditable(guardada.anioId());

        // grupos que faltan
        Map<String, GrupoResponse> grupos = new HashMap<>();
        for (GrupoResponse grupo : estructuraService.listarGrupos(anio.id())) {
            grupos.put(claveGrupo(grupo.gradoId(), grupo.nombre()), grupo);
        }

        int gruposCreados = 0;
        for (FilaImportada fila : guardada.filas()) {
            String clave = claveGrupo(fila.gradoId(), fila.grupoNombre());
            if (!grupos.containsKey(clave)) {
                GrupoResponse nuevo = estructuraService.crearGrupo(anio.id(), fila.gradoId(), fila.grupoNombre().toUpperCase());
                grupos.put(clave, nuevo);
                gruposCreados++;
            }
        }

        // estudiantes que ya existen
        List<String> documentos = new ArrayList<>();
        for (FilaImportada fila : guardada.filas()) {
            documentos.add(fila.nroDoc());
        }

        Map<String, Estudiante> existentes = new HashMap<>();
        for (Estudiante estudiante : estudianteRepository.buscarPorDocumentos(documentos)) {
            existentes.put(estudiante.getNroDoc(), estudiante);
        }

        List<String> documentosNuevos = new ArrayList<>();
        for (String documento : documentos) {
            if (!existentes.containsKey(documento)) {
                documentosNuevos.add(documento);
            }
        }

        // falla con conflicto si entre la vista previa y ahora alguien uso uno de estos documentos
        Map<String, Usuario> usuariosNuevos = usuarioService.crearVarios(documentosNuevos, Rol.ESTUDIANTE);

        int creados = 0;
        int actualizados = 0;

        for (FilaImportada fila : guardada.filas()) {
            Estudiante estudiante = existentes.get(fila.nroDoc());

            if (estudiante == null) {
                estudiante = new Estudiante();
                estudiante.setInstitucionId(institucionId);
                estudiante.setUsuario(usuariosNuevos.get(fila.nroDoc()));
                estudiante.setCodigo(CodigoAleatorio.generar());
                estudiante.setCodigoQr(CodigoAleatorio.generar());
                creados++;
            } else {
                actualizados++;
            }

            estudiante.setTipoDoc(fila.tipoDoc());
            estudiante.setNroDoc(fila.nroDoc());
            estudiante.setNombres(fila.nombres());
            estudiante.setApellidos(fila.apellidos());
            // una celda vacia no borra lo que ya tenia
            if (fila.genero() != null) {
                estudiante.setGenero(fila.genero());
            }
            if (fila.fechaNacimiento() != null) {
                estudiante.setFechaNacimiento(LocalDate.parse(fila.fechaNacimiento()));
            }
            if (fila.celular() != null) {
                estudiante.setCelular(fila.celular());
            }
            if (fila.correo() != null) {
                estudiante.setCorreo(fila.correo());
            }
            if (fila.direccion() != null) {
                estudiante.setDireccion(fila.direccion());
            }
            if (fila.barrio() != null) {
                estudiante.setBarrio(fila.barrio());
            }
            if (fila.eps() != null) {
                estudiante.setEps(fila.eps());
            }
            if (fila.rh() != null) {
                estudiante.setRh(fila.rh());
            }
            if (fila.condicionesSalud() != null) {
                estudiante.setCondicionesSalud(fila.condicionesSalud());
            }
            estudianteRepository.save(estudiante);

            // matricula del anio: nueva, promocion o repite segun el anio anterior; si ya tenia, se mueve
            GrupoResponse grupo = grupos.get(claveGrupo(fila.gradoId(), fila.grupoNombre()));
            matriculaService.ubicar(estudiante.getId(), grupo, "Importacion de Excel");

            if (fila.familiarNombres() != null) {
                guardarFamiliar(institucionId, estudiante, fila);
            }
        }

        bitacoraService.registrar("IMPORTAR_ESTUDIANTES", "estudiante", null,
                creados + " creados, " + actualizados + " actualizados, año " + anio.anio());

        return new ResultadoImportacionResponse(creados, actualizados, gruposCreados);
    }

    // el familiar del excel queda como el primero; los otros dos no se tocan
    private void guardarFamiliar(Long institucionId, Estudiante estudiante, FilaImportada fila) {

        Familiar familiar = familiarRepository.findByEstudianteIdAndPosicion(estudiante.getId(), 1);

        if (familiar == null) {
            familiar = new Familiar();
            familiar.setInstitucionId(institucionId);
            familiar.setEstudiante(estudiante);
            familiar.setPosicion(1);
        }

        familiar.setNombres(fila.familiarNombres());
        familiar.setParentesco(fila.familiarParentesco());
        familiar.setCelular(fila.familiarCelular());
        familiar.setRecibeSms(fila.familiarCelular() != null);
        familiarRepository.save(familiar);
    }

    // ---------------------------------------------------------------- exportar

    @Override
    @Transactional(readOnly = true)
    public byte[] exportar(String texto, Long gradoId, Long grupoId, Boolean activo) {

        TenantSupport.requireTenant(em);

        AnioLectivoResponse activoActual = estructuraService.anioActivo();
        Long anioId = null;
        if (activoActual != null) {
            anioId = activoActual.id();
        }

        String busqueda = null;
        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        Page<EstudianteFila> page = estudianteRepository.buscar(
                anioId, busqueda, gradoId, grupoId, activo, false, Pageable.unpaged());

        // mismas columnas de la plantilla (mas el estado): el archivo sirve de base para el anio siguiente
        String[] columnas = new String[ExcelEstudiantes.COLUMNAS.length + 1];
        for (int i = 0; i < ExcelEstudiantes.COLUMNAS.length; i++) {
            columnas[i] = ExcelEstudiantes.COLUMNAS[i];
        }
        columnas[ExcelEstudiantes.COLUMNAS.length] = "ESTADO";

        List<String> documentos = new ArrayList<>();
        for (EstudianteFila fila : page.getContent()) {
            documentos.add(fila.getNroDoc());
        }

        Map<String, Estudiante> completos = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        if (!documentos.isEmpty()) {
            for (Estudiante estudiante : estudianteRepository.buscarPorDocumentos(documentos)) {
                completos.put(estudiante.getNroDoc(), estudiante);
                ids.add(estudiante.getId());
            }
        }

        Map<Long, Familiar> primerFamiliar = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Familiar familiar : familiarRepository.findByEstudianteIdInAndPosicion(ids, 1)) {
                primerFamiliar.put(familiar.getEstudiante().getId(), familiar);
            }
        }

        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String[]> filas = new ArrayList<>();

        for (EstudianteFila fila : page.getContent()) {
            Estudiante estudiante = completos.get(fila.getNroDoc());
            Familiar familiar = primerFamiliar.get(estudiante.getId());

            String fecha = null;
            if (estudiante.getFechaNacimiento() != null) {
                fecha = estudiante.getFechaNacimiento().format(formatoFecha);
            }

            String familiarNombre = null;
            String familiarParentesco = null;
            String familiarCelular = null;
            if (familiar != null) {
                familiarNombre = familiar.getNombres();
                if (familiar.getApellidos() != null) {
                    familiarNombre = familiarNombre + " " + familiar.getApellidos();
                }
                familiarParentesco = familiar.getParentesco();
                familiarCelular = familiar.getCelular();
            }

            String estado = "Inactivo";
            if (Boolean.TRUE.equals(fila.getActivo())) {
                estado = "Activo";
            }

            filas.add(new String[] {
                    estudiante.getTipoDoc(), estudiante.getNroDoc(), estudiante.getNombres(), estudiante.getApellidos(),
                    estudiante.getGenero(), fecha, estudiante.getCelular(),
                    fila.getGradoNombre(), fila.getGrupoNombre(),
                    familiarNombre, familiarParentesco, familiarCelular,
                    estudiante.getCorreo(), estudiante.getDireccion(), estudiante.getBarrio(),
                    estudiante.getEps(), estudiante.getRh(), estudiante.getCondicionesSalud(),
                    estado
            });
        }

        return ExcelEstudiantes.exportar(columnas, filas);
    }

    // ---------------------------------------------------------------- ayudas

    private AnioLectivoResponse anioDestino(Long anioId) {

        if (anioId != null) {
            return estructuraService.buscarAnioEditable(anioId);
        }

        AnioLectivoResponse activo = estructuraService.anioActivo();

        if (activo == null) {
            throw ApiException.invalido("No hay año lectivo activo. Crea o activa uno en Grados y grupos");
        }

        return activo;
    }

    // se aceptan: el nombre (Sexto), el numero (6), con grado (6°) y otras formas comunes
    private Map<String, GradoResponse> mapaDeGrados() {

        Map<String, GradoResponse> mapa = new HashMap<>();
        Map<Integer, GradoResponse> porOrden = new HashMap<>();

        for (GradoResponse grado : estructuraService.listarGrados()) {
            mapa.put(normalizar(grado.nombre()), grado);
            porOrden.put(grado.orden(), grado);

            if (grado.orden() >= 0) {
                mapa.put(String.valueOf(grado.orden()), grado);
            }
        }

        for (Map.Entry<String, Integer> otro : OTROS_NOMBRES_GRADO.entrySet()) {
            GradoResponse grado = porOrden.get(otro.getValue());
            if (grado != null) {
                mapa.put(otro.getKey(), grado);
            }
        }

        return mapa;
    }

    private String normalizar(String texto) {

        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase().replace("°", "").replace("º", "").replace("grado", "").trim();

        // "06" -> "6"
        if (limpio.matches("0+[0-9]+")) {
            limpio = limpio.replaceFirst("^0+", "");
        }

        return limpio;
    }

    private String claveGrupo(Long gradoId, String nombreGrupo) {
        return gradoId + "|" + nombreGrupo.trim().toLowerCase();
    }

    // devuelve la fecha en formato aaaa-mm-dd o null si no se entiende
    private String leerFecha(String texto) {

        String[] formatos = {"d/M/yyyy", "d-M-yyyy", "yyyy-M-d"};

        for (String formato : formatos) {
            try {
                // la edad se revisa aparte (FechaNacimiento), con un mensaje que dice que esta mal
                LocalDate fecha = LocalDate.parse(texto, DateTimeFormatter.ofPattern(formato));
                return fecha.toString();
            } catch (DateTimeParseException e) {
                // se prueba con el siguiente formato
            }
        }

        return null;
    }

    private void guardar(Long institucionId, String token, ImportacionGuardada datos) {

        try {
            String json = mapper.writeValueAsString(datos);
            redis.opsForValue().set(clave(institucionId, token), json, DURACION_VISTA_PREVIA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la vista previa", e);
        }
    }

    // se lee y se borra: una vista previa solo se puede confirmar una vez
    private ImportacionGuardada sacar(Long institucionId, String token) {

        String json = redis.opsForValue().getAndDelete(clave(institucionId, token));

        if (json == null) {
            throw ApiException.noEncontrado("La vista previa vencio o ya se confirmo. Sube el archivo otra vez");
        }

        try {
            return mapper.readValue(json, ImportacionGuardada.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la vista previa", e);
        }
    }

    // lleva la institucion: el token de un colegio no sirve en otro
    private String clave(Long institucionId, String token) {
        return "import:est:" + institucionId + ":" + token;
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
