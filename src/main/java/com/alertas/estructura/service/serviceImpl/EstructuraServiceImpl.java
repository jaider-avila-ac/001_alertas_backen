package com.alertas.estructura.service.serviceImpl;

import com.alertas.bitacora.service.BitacoraService;
import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GradoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.model.AnioLectivo;
import com.alertas.estructura.model.Grado;
import com.alertas.estructura.model.Grupo;
import com.alertas.estructura.repository.AnioLectivoRepository;
import com.alertas.estructura.repository.GradoRepository;
import com.alertas.estructura.repository.GrupoRepository;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstructuraServiceImpl implements EstructuraService {

    // la posicion en el arreglo es el orden + 2 (prejardin es -2)
    private static final String[] GRADOS = {
            "Prejardin", "Jardin", "Transicion",
            "Primero", "Segundo", "Tercero", "Cuarto", "Quinto",
            "Sexto", "Septimo", "Octavo", "Noveno", "Decimo", "Once"
    };

    private final GradoRepository gradoRepository;
    private final AnioLectivoRepository anioRepository;
    private final GrupoRepository grupoRepository;
    private final BitacoraService bitacoraService;
    private final EntityManager em;

    public EstructuraServiceImpl(
            GradoRepository gradoRepository,
            AnioLectivoRepository anioRepository,
            GrupoRepository grupoRepository,
            BitacoraService bitacoraService,
            EntityManager em) {

        this.gradoRepository = gradoRepository;
        this.anioRepository = anioRepository;
        this.grupoRepository = grupoRepository;
        this.bitacoraService = bitacoraService;
        this.em = em;
    }

    @Override
    @Transactional
    public void crearDatosIniciales() {

        Long institucionId = TenantSupport.requireTenant(em);

        List<Grado> grados = new ArrayList<>();

        for (int i = 0; i < GRADOS.length; i++) {
            Grado grado = new Grado();
            grado.setInstitucionId(institucionId);
            grado.setNombre(GRADOS[i]);
            grado.setOrden(i - 2);
            // prejardin y jardin casi ningun colegio publico los tiene, quedan apagados
            grado.setActivo(i >= 2);
            grados.add(grado);
        }

        gradoRepository.saveAll(grados);

        AnioLectivo anio = new AnioLectivo();
        anio.setInstitucionId(institucionId);
        anio.setAnio(LocalDate.now().getYear());
        anio.setActivo(true);

        anioRepository.save(anio);
    }

    // ---------------------------------------------------------------- grados

    @Override
    @Transactional(readOnly = true)
    public List<GradoResponse> listarGrados() {

        TenantSupport.requireTenant(em);

        List<GradoResponse> respuesta = new ArrayList<>();

        for (Grado grado : gradoRepository.findAllByOrderByOrdenAsc()) {
            respuesta.add(GradoResponse.desde(grado));
        }

        return respuesta;
    }

    @Override
    @Transactional
    public GradoResponse cambiarEstadoGrado(Long gradoId, boolean activo) {

        TenantSupport.requireTenant(em);
        Grado grado = obtenerGrado(gradoId);

        if (!activo) {
            AnioLectivo activoActual = anioRepository.findByActivoTrue().orElse(null);

            if (activoActual != null && grupoRepository.contarDelGradoDesde(gradoId, activoActual.getAnio()) > 0) {
                throw ApiException.conflicto("El grado " + grado.getNombre()
                        + " tiene grupos en el anio activo o en uno futuro. Borra esos grupos antes de apagarlo");
            }
        }

        grado.setActivo(activo);
        return GradoResponse.desde(grado);
    }

    // ---------------------------------------------------------------- anios lectivos

    @Override
    @Transactional(readOnly = true)
    public List<AnioLectivoResponse> listarAnios() {

        TenantSupport.requireTenant(em);

        List<AnioLectivoResponse> respuesta = new ArrayList<>();

        for (AnioLectivo anio : anioRepository.findAllByOrderByAnioDesc()) {
            long totalGrupos = grupoRepository.countByAnioId(anio.getId());
            respuesta.add(AnioLectivoResponse.desde(anio, totalGrupos));
        }

        return respuesta;
    }

    @Override
    @Transactional
    public AnioLectivoResponse crearAnio(int anio) {

        Long institucionId = TenantSupport.requireTenant(em);

        if (anioRepository.existsByAnio(anio)) {
            throw ApiException.conflicto("El anio " + anio + " ya existe");
        }

        // se crea apagado: el admin lo prepara (grupos, estudiantes) y lo activa cuando empiece
        AnioLectivo nuevo = new AnioLectivo();
        nuevo.setInstitucionId(institucionId);
        nuevo.setAnio(anio);
        nuevo.setActivo(false);

        anioRepository.save(nuevo);
        bitacoraService.registrar("CREAR_ANIO", "anio_lectivo", nuevo.getId(), String.valueOf(anio));

        return AnioLectivoResponse.desde(nuevo, 0);
    }

    @Override
    @Transactional
    public AnioLectivoResponse activarAnio(Long anioId) {

        TenantSupport.requireTenant(em);
        AnioLectivo anio = obtenerAnio(anioId);

        if (anio.isActivo()) {
            throw ApiException.conflicto("El anio " + anio.getAnio() + " ya es el activo");
        }

        anioRepository.desactivarTodos();

        // desactivarTodos limpia el contexto de jpa, hay que volver a leerlo
        AnioLectivo aActivar = obtenerAnio(anioId);
        aActivar.setActivo(true);

        bitacoraService.registrar("ACTIVAR_ANIO", "anio_lectivo", anioId, String.valueOf(aActivar.getAnio()));
        return AnioLectivoResponse.desde(aActivar, grupoRepository.countByAnioId(anioId));
    }

    @Override
    @Transactional
    public void borrarAnio(Long anioId) {

        TenantSupport.requireTenant(em);
        AnioLectivo anio = obtenerAnio(anioId);

        if (anio.isActivo()) {
            throw ApiException.conflicto("No se puede borrar el anio activo");
        }

        if (grupoRepository.countByAnioId(anioId) > 0) {
            throw ApiException.conflicto("El anio " + anio.getAnio() + " tiene grupos, borralos primero");
        }

        anioRepository.delete(anio);
        bitacoraService.registrar("BORRAR_ANIO", "anio_lectivo", anioId, String.valueOf(anio.getAnio()));
    }

    @Override
    @Transactional(readOnly = true)
    public AnioLectivoResponse anioActivo() {

        TenantSupport.requireTenant(em);

        AnioLectivo activo = anioRepository.findByActivoTrue().orElse(null);

        if (activo == null) {
            return null;
        }

        return AnioLectivoResponse.desde(activo, grupoRepository.countByAnioId(activo.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public AnioLectivoResponse buscarAnioEditable(Long anioId) {

        TenantSupport.requireTenant(em);

        AnioLectivo anio = obtenerAnio(anioId);
        validarAnioEditable(anio);

        return AnioLectivoResponse.desde(anio, grupoRepository.countByAnioId(anioId));
    }

    // ---------------------------------------------------------------- grupos

    @Override
    @Transactional(readOnly = true)
    public GrupoResponse buscarGrupo(Long grupoId) {

        TenantSupport.requireTenant(em);

        Grupo grupo = obtenerGrupo(grupoId);
        return GrupoResponse.desde(grupo, grupoRepository.contarEstudiantes(grupoId), grupoRepository.contarAlertas(grupoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoResponse> listarGrupos(Long anioId) {

        TenantSupport.requireTenant(em);

        AnioLectivo anio;

        if (anioId == null) {
            anio = anioRepository.findByActivoTrue().orElse(null);

            if (anio == null) {
                return new ArrayList<>();
            }
        } else {
            anio = obtenerAnio(anioId);
        }

        // una sola consulta para los totales de todos los grupos del anio
        Map<Long, Long> estudiantesPorGrupo = new HashMap<>();

        for (Object[] fila : grupoRepository.contarEstudiantesPorGrupo(anio.getId())) {
            Long grupoId = ((Number) fila[0]).longValue();
            Long total = ((Number) fila[1]).longValue();
            estudiantesPorGrupo.put(grupoId, total);
        }

        Map<Long, Long> alertasPorGrupo = new HashMap<>();

        for (Object[] fila : grupoRepository.contarAlertasPorGrupo(anio.getId())) {
            alertasPorGrupo.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        List<GrupoResponse> respuesta = new ArrayList<>();

        for (Grupo grupo : grupoRepository.buscarPorAnio(anio.getId())) {
            long total = estudiantesPorGrupo.getOrDefault(grupo.getId(), 0L);
            long alertas = alertasPorGrupo.getOrDefault(grupo.getId(), 0L);
            respuesta.add(GrupoResponse.desde(grupo, total, alertas));
        }

        return respuesta;
    }

    @Override
    @Transactional
    public GrupoResponse crearGrupo(Long anioId, Long gradoId, String nombre) {

        Long institucionId = TenantSupport.requireTenant(em);

        AnioLectivo anio = obtenerAnio(anioId);
        Grado grado = obtenerGrado(gradoId);
        String nombreLimpio = nombre.trim();

        validarAnioEditable(anio);

        if (!grado.isActivo()) {
            throw ApiException.invalido("El grado " + grado.getNombre() + " esta apagado, activalo primero");
        }

        if (grupoRepository.existeNombre(anioId, gradoId, nombreLimpio, null)) {
            throw ApiException.conflicto("Ya existe el grupo " + nombreLimpio + " en " + grado.getNombre());
        }

        Grupo grupo = new Grupo();
        grupo.setInstitucionId(institucionId);
        grupo.setAnio(anio);
        grupo.setGrado(grado);
        grupo.setNombre(nombreLimpio);

        grupoRepository.save(grupo);
        return GrupoResponse.desde(grupo, 0, 0);
    }

    @Override
    @Transactional
    public GrupoResponse renombrarGrupo(Long grupoId, String nombre) {

        TenantSupport.requireTenant(em);

        Grupo grupo = obtenerGrupo(grupoId);
        String nombreLimpio = nombre.trim();

        validarAnioEditable(grupo.getAnio());

        if (grupoRepository.existeNombre(grupo.getAnio().getId(), grupo.getGrado().getId(), nombreLimpio, grupoId)) {
            throw ApiException.conflicto("Ya existe el grupo " + nombreLimpio + " en " + grupo.getGrado().getNombre());
        }

        grupo.setNombre(nombreLimpio);
        return GrupoResponse.desde(grupo, grupoRepository.contarEstudiantes(grupoId), grupoRepository.contarAlertas(grupoId));
    }

    @Override
    @Transactional
    public void borrarGrupo(Long grupoId) {

        TenantSupport.requireTenant(em);

        Grupo grupo = obtenerGrupo(grupoId);
        validarAnioEditable(grupo.getAnio());

        long estudiantes = grupoRepository.contarEstudiantes(grupoId);

        if (estudiantes > 0) {
            throw ApiException.conflicto("El grupo tiene " + estudiantes + " estudiantes, cambialos de grupo primero");
        }

        // estudiantes que pasaron por el grupo (retirados o movidos): es historial
        if (grupoRepository.contarHistorial(grupoId) > 0) {
            throw ApiException.conflicto("Por el grupo ya pasaron estudiantes, queda como historial y no se puede borrar");
        }

        // las alertas guardan el grupo del momento, borrarlo romperia el historial
        if (grupoRepository.contarAlertas(grupoId) > 0) {
            throw ApiException.conflicto("El grupo tiene alertas registradas, no se puede borrar");
        }

        grupoRepository.delete(grupo);
        bitacoraService.registrar("BORRAR_GRUPO", "grupo", grupoId,
                grupo.getGrado().getNombre() + " " + grupo.getNombre() + " " + grupo.getAnio().getAnio());
    }

    // ---------------------------------------------------------------- ayudas

    // los anios que ya pasaron quedan como historial, solo se tocan el activo y los que vienen
    private void validarAnioEditable(AnioLectivo anio) {

        AnioLectivo activo = anioRepository.findByActivoTrue().orElse(null);

        if (activo != null && anio.getAnio() < activo.getAnio()) {
            throw ApiException.invalido("El anio " + anio.getAnio() + " ya paso, sus grupos no se pueden cambiar");
        }
    }

    private Grado obtenerGrado(Long gradoId) {

        Grado grado = gradoRepository.findById(gradoId).orElse(null);

        if (grado == null) {
            throw ApiException.noEncontrado("El grado no existe");
        }

        return grado;
    }

    private AnioLectivo obtenerAnio(Long anioId) {

        AnioLectivo anio = anioRepository.findById(anioId).orElse(null);

        if (anio == null) {
            throw ApiException.noEncontrado("El anio lectivo no existe");
        }

        return anio;
    }

    private Grupo obtenerGrupo(Long grupoId) {

        Grupo grupo = grupoRepository.findById(grupoId).orElse(null);

        if (grupo == null) {
            throw ApiException.noEncontrado("El grupo no existe");
        }

        return grupo;
    }
}
