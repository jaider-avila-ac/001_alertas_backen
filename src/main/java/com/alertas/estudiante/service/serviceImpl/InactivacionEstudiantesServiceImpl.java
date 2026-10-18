package com.alertas.estudiante.service.serviceImpl;

import com.alertas.bitacora.service.BitacoraService;
import com.alertas.estudiante.dto.EstudianteAInactivar;
import com.alertas.estudiante.dto.ResultadoInactivacionResponse;
import com.alertas.estudiante.dto.VistaPreviaInactivacionResponse;
import com.alertas.estudiante.excel.ExcelInactivacion;
import com.alertas.estudiante.model.Estudiante;
import com.alertas.estudiante.repository.EstudianteRepository;
import com.alertas.estudiante.service.InactivacionEstudiantesService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.ErrorFila;
import com.alertas.shared.exception.ApiException;
import com.alertas.shared.validacion.Formato;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InactivacionEstudiantesServiceImpl implements InactivacionEstudiantesService {

    private static final int MAXIMO_FILAS = 3000;
    private static final int MAXIMO_MOSTRADOS = 100;
    private static final Duration DURACION_VISTA_PREVIA = Duration.ofMinutes(15);

    private final EstudianteRepository estudianteRepository;
    private final UsuarioService usuarioService;
    private final BitacoraService bitacoraService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public InactivacionEstudiantesServiceImpl(
            EstudianteRepository estudianteRepository,
            UsuarioService usuarioService,
            BitacoraService bitacoraService,
            StringRedisTemplate redis,
            ObjectMapper mapper,
            EntityManager em) {

        this.estudianteRepository = estudianteRepository;
        this.usuarioService = usuarioService;
        this.bitacoraService = bitacoraService;
        this.redis = redis;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public byte[] plantilla() {
        return ExcelInactivacion.plantilla();
    }

    @Override
    @Transactional(readOnly = true)
    public VistaPreviaInactivacionResponse vistaPrevia(InputStream archivo) {

        Long institucionId = TenantSupport.requireTenant(em);

        List<Map<String, String>> filas = ExcelInactivacion.leer(archivo, MAXIMO_FILAS);

        if (filas.isEmpty()) {
            throw ApiException.invalido("El archivo no tiene documentos");
        }

        // documento -> fila del excel, para decir en que fila esta el problema
        Map<String, Integer> filaDelDocumento = new HashMap<>();
        List<ErrorFila> errores = new ArrayList<>();

        for (Map<String, String> datos : filas) {
            int numero = Integer.parseInt(datos.get("_fila"));
            String documento = Formato.documento(datos.get(ExcelInactivacion.NUMERO_DOCUMENTO));

            if (documento == null || documento.isEmpty()) {
                errores.add(new ErrorFila(numero, "falta el numero de documento"));
            } else if (filaDelDocumento.containsKey(documento)) {
                errores.add(new ErrorFila(numero, "el documento " + documento + " esta repetido en el archivo"));
            } else {
                filaDelDocumento.put(documento, numero);
            }
        }

        Map<String, Estudiante> encontrados = new HashMap<>();
        if (!filaDelDocumento.isEmpty()) {
            for (Estudiante estudiante : estudianteRepository.buscarPorDocumentos(filaDelDocumento.keySet())) {
                encontrados.put(estudiante.getNroDoc(), estudiante);
            }
        }

        List<String> aInactivar = new ArrayList<>();
        List<EstudianteAInactivar> mostrados = new ArrayList<>();
        int yaInactivos = 0;

        for (Map.Entry<String, Integer> entrada : filaDelDocumento.entrySet()) {
            Estudiante estudiante = encontrados.get(entrada.getKey());

            if (estudiante == null) {
                errores.add(new ErrorFila(entrada.getValue(), "no hay un estudiante con el documento " + entrada.getKey()));
            } else if (!estudiante.getUsuario().isActivo()) {
                yaInactivos++;
            } else {
                aInactivar.add(estudiante.getNroDoc());
            }
        }

        errores.sort((a, b) -> Integer.compare(a.fila(), b.fila()));

        // en el orden del excel
        aInactivar.sort((a, b) -> Integer.compare(filaDelDocumento.get(a), filaDelDocumento.get(b)));
        for (String documento : aInactivar) {
            if (mostrados.size() >= MAXIMO_MOSTRADOS) {
                break;
            }
            Estudiante estudiante = encontrados.get(documento);
            mostrados.add(new EstudianteAInactivar(
                    estudiante.getNroDoc(), estudiante.getNombres(), estudiante.getApellidos(), estudiante.getGrupo()));
        }

        String token = null;

        if (errores.isEmpty() && !aInactivar.isEmpty()) {
            token = UUID.randomUUID().toString();
            guardar(institucionId, token, aInactivar);
        }

        int totalErrores = errores.size();
        List<ErrorFila> erroresMostrados = errores;
        if (errores.size() > MAXIMO_MOSTRADOS) {
            erroresMostrados = errores.subList(0, MAXIMO_MOSTRADOS);
        }

        return new VistaPreviaInactivacionResponse(
                token, filas.size(), aInactivar.size(), yaInactivos, mostrados, erroresMostrados, totalErrores);
    }

    @Override
    @Transactional
    public ResultadoInactivacionResponse confirmar(String token) {

        Long institucionId = TenantSupport.requireTenant(em);
        List<String> documentos = sacar(institucionId, token);

        // se vuelve a revisar: entre la vista previa y ahora alguien pudo inactivarlos a mano
        List<Long> usuarioIds = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Estudiante estudiante : estudianteRepository.buscarPorDocumentos(documentos)) {
            if (estudiante.getUsuario().isActivo() && vistos.add(estudiante.getNroDoc())) {
                usuarioIds.add(estudiante.getUsuario().getId());
            }
        }

        int inactivados = 0;
        if (!usuarioIds.isEmpty()) {
            // tambien les cierra la sesion
            inactivados = usuarioService.cambiarEstadoMasivo(null, usuarioIds, false);
        }

        bitacoraService.registrar("INACTIVAR_ESTUDIANTES", "estudiante", null, inactivados + " inactivados desde excel");

        return new ResultadoInactivacionResponse(inactivados);
    }

    private void guardar(Long institucionId, String token, List<String> documentos) {

        try {
            String json = mapper.writeValueAsString(documentos);
            redis.opsForValue().set(clave(institucionId, token), json, DURACION_VISTA_PREVIA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la vista previa", e);
        }
    }

    // se lee y se borra: una vista previa solo se puede confirmar una vez
    private List<String> sacar(Long institucionId, String token) {

        String json = redis.opsForValue().getAndDelete(clave(institucionId, token));

        if (json == null) {
            throw ApiException.noEncontrado("La vista previa vencio o ya se confirmo. Sube el archivo otra vez");
        }

        try {
            String[] documentos = mapper.readValue(json, String[].class);
            List<String> lista = new ArrayList<>();
            for (String documento : documentos) {
                lista.add(documento);
            }
            return lista;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la vista previa", e);
        }
    }

    // lleva la institucion: el token de un colegio no sirve en otro
    private String clave(Long institucionId, String token) {
        return "inactivar:est:" + institucionId + ":" + token;
    }
}
