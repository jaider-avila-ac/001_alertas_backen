package com.alertas.matricula.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.matricula.dto.MatriculaResponse;
import com.alertas.matricula.dto.MovimientoResponse;
import com.alertas.matricula.model.Matricula;
import com.alertas.matricula.model.MatriculaMovimiento;
import com.alertas.matricula.repository.MatriculaFila;
import com.alertas.matricula.repository.MatriculaRepository;
import com.alertas.matricula.repository.MovimientoFila;
import com.alertas.matricula.repository.MovimientoRepository;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.shared.TenantSupport;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatriculaServiceImpl implements MatriculaService {

    private final MatriculaRepository repository;
    private final MovimientoRepository movimientoRepository;
    private final EstructuraService estructuraService;
    private final EntityManager em;

    public MatriculaServiceImpl(
            MatriculaRepository repository,
            MovimientoRepository movimientoRepository,
            EstructuraService estructuraService,
            EntityManager em) {

        this.repository = repository;
        this.movimientoRepository = movimientoRepository;
        this.estructuraService = estructuraService;
        this.em = em;
    }

    // ---------------------------------------------------------------- ubicar

    @Override
    @Transactional
    public boolean ubicar(Long estudianteId, GrupoResponse grupo, String motivo) {

        Long institucionId = TenantSupport.requireTenant(em);

        // un anio que ya paso es historial
        estructuraService.buscarAnioEditable(grupo.anioId());

        Matricula matricula = repository.findByEstudianteIdAndAnioId(estudianteId, grupo.anioId());

        if (matricula == null) {
            matricula = new Matricula();
            matricula.setInstitucionId(institucionId);
            matricula.setEstudianteId(estudianteId);
            matricula.setAnioId(grupo.anioId());
            matricula.setGrupoId(grupo.id());
            ajustarOrigen(matricula, grupo);
            repository.save(matricula);
            return false;
        }

        boolean reabierta = false;

        if (Matricula.RETIRADA.equals(matricula.getEstado())) {
            matricula.reabrir();
            reabierta = true;
        }

        if (!matricula.getGrupoId().equals(grupo.id())) {
            registrarMovimiento(matricula, grupo.id(), motivo);
            matricula.setGrupoId(grupo.id());

            // si cambio de grado puede que ahora repita (o que ya no)
            ajustarOrigen(matricula, grupo);
        }

        return reabierta;
    }

    // compara con el anio anterior: mismo grado = repite, otro grado = promocion, sin anterior = nueva.
    // la matricula anterior queda cerrada con el resultado que corresponde
    private void ajustarOrigen(Matricula matricula, GrupoResponse grupo) {

        Matricula anterior = repository.buscarAnterior(matricula.getEstudianteId(), grupo.anio());

        if (anterior == null) {
            matricula.setOrigen(Matricula.NUEVA);
            return;
        }

        GrupoResponse grupoAnterior = estructuraService.buscarGrupo(anterior.getGrupoId());
        boolean repite = grupoAnterior.gradoOrden() == grupo.gradoOrden();

        if (repite) {
            matricula.setOrigen(Matricula.REPITE);
        } else {
            matricula.setOrigen(Matricula.PROMOCION);
        }

        // una retirada no cambia: el estudiante volvio despues de irse
        if (Matricula.RETIRADA.equals(anterior.getEstado())) {
            return;
        }

        String resultado = Matricula.PROMOVIDA;
        if (repite) {
            resultado = Matricula.REPROBADA;
        }

        // si ya estaba cerrada solo se corrige el resultado, la fecha de cierre se conserva
        if (anterior.isActiva()) {
            anterior.cerrar(resultado, null);
        } else {
            anterior.setEstado(resultado);
        }
    }

    private void registrarMovimiento(Matricula matricula, Long grupoNuevoId, String motivo) {

        MatriculaMovimiento movimiento = new MatriculaMovimiento();
        movimiento.setInstitucionId(matricula.getInstitucionId());
        movimiento.setMatriculaId(matricula.getId());
        movimiento.setAnioId(matricula.getAnioId());
        movimiento.setGrupoAnteriorId(matricula.getGrupoId());
        movimiento.setGrupoNuevoId(grupoNuevoId);
        movimiento.setMotivo(vacioANull(motivo));
        movimiento.setUsuarioId(UsuarioAutenticado.actual().id());
        movimientoRepository.save(movimiento);
    }

    // ---------------------------------------------------------------- retirar

    @Override
    @Transactional
    public int retirar(Long estudianteId, String motivo) {

        TenantSupport.requireTenant(em);

        List<Matricula> activas = repository.findByEstudianteIdAndEstado(estudianteId, Matricula.ACTIVA);

        for (Matricula matricula : activas) {
            matricula.cerrar(Matricula.RETIRADA, vacioANull(motivo));
        }

        return activas.size();
    }

    @Override
    @Transactional
    public int cerrarActivas(Collection<Long> estudianteIds, String motivo) {

        TenantSupport.requireTenant(em);

        if (estudianteIds.isEmpty()) {
            return 0;
        }

        int cerradas = 0;

        for (Long estudianteId : estudianteIds) {
            for (Matricula matricula : repository.findByEstudianteIdAndEstado(estudianteId, Matricula.ACTIVA)) {
                matricula.cerrar(Matricula.RETIRADA, motivo);
                cerradas++;
            }
        }

        return cerradas;
    }

    // ---------------------------------------------------------------- consultar

    @Override
    @Transactional(readOnly = true)
    public List<MatriculaResponse> trayectoria(Long estudianteId) {

        TenantSupport.requireTenant(em);

        AnioLectivoResponse activo = estructuraService.anioActivo();
        List<MovimientoFila> movimientos = repository.movimientos(estudianteId);
        List<MatriculaResponse> respuesta = new ArrayList<>();

        for (MatriculaFila fila : repository.trayectoria(estudianteId)) {
            List<MovimientoResponse> suyos = new ArrayList<>();

            for (MovimientoFila movimiento : movimientos) {
                if (movimiento.getMatriculaId().equals(fila.getId())) {
                    suyos.add(new MovimientoResponse(
                            movimiento.getFecha(),
                            movimiento.getGradoAnterior(),
                            movimiento.getGrupoAnterior(),
                            movimiento.getGradoNuevo(),
                            movimiento.getGrupoNuevo(),
                            movimiento.getMotivo()));
                }
            }

            // el anio activo y los que vienen se pueden cambiar, los que pasaron no
            boolean editable = activo == null || fila.getAnio() >= activo.anio();

            respuesta.add(new MatriculaResponse(
                    fila.getAnioId(),
                    fila.getAnio(),
                    Boolean.TRUE.equals(fila.getAnioActivo()),
                    fila.getGrupoId(),
                    fila.getGradoNombre(),
                    fila.getGrupoNombre(),
                    fila.getEstado(),
                    fila.getOrigen(),
                    fila.getFechaMatricula(),
                    fila.getFechaCierre(),
                    fila.getMotivoCierre(),
                    editable,
                    suyos));
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public Matricula delAnioActivo(Long estudianteId) {

        TenantSupport.requireTenant(em);

        AnioLectivoResponse activo = estructuraService.anioActivo();

        if (activo == null) {
            return null;
        }

        return repository.findByEstudianteIdAndAnioId(estudianteId, activo.id());
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
