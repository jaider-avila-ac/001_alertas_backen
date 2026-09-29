package com.alertas.cita.service.serviceImpl;

import com.alertas.alerta.model.Alerta;
import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.cita.dto.AgendarCitaRequest;
import com.alertas.cita.dto.AlertaDeCita;
import com.alertas.cita.dto.CancelarCitaRequest;
import com.alertas.cita.dto.CitaResponse;
import com.alertas.cita.dto.ReprogramarCitaRequest;
import com.alertas.cita.dto.ResultadoAlerta;
import com.alertas.cita.dto.ResultadoCitaRequest;
import com.alertas.cita.model.Cita;
import com.alertas.cita.repository.CitaRepository;
import com.alertas.cita.service.CitaService;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.matricula.model.Matricula;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitaServiceImpl implements CitaService {

    private static final int MAXIMO_DIAS_AGENDA = 42;

    private final CitaRepository repository;
    private final AlertaRepository alertaRepository;
    private final EstudianteService estudianteService;
    private final MatriculaService matriculaService;
    private final PersonalService personalService;
    private final EntityManager em;

    public CitaServiceImpl(
            CitaRepository repository,
            AlertaRepository alertaRepository,
            EstudianteService estudianteService,
            MatriculaService matriculaService,
            PersonalService personalService,
            EntityManager em) {

        this.repository = repository;
        this.alertaRepository = alertaRepository;
        this.estudianteService = estudianteService;
        this.matriculaService = matriculaService;
        this.personalService = personalService;
        this.em = em;
    }

    // ---------------------------------------------------------------- agendar

    @Override
    @Transactional
    public CitaResponse agendar(AgendarCitaRequest request) {

        TenantSupport.requireTenant(em);

        PsicorientadorBasico yo = yo();
        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(request.estudianteCodigo());

        if (!estudiante.activo()) {
            throw ApiException.conflicto("El estudiante esta inactivo");
        }

        Matricula matricula = matriculaService.delAnioActivo(estudiante.id());
        if (matricula == null || Matricula.RETIRADA.equals(matricula.getEstado())) {
            throw ApiException.conflicto("El estudiante no tiene matricula en el anio activo");
        }

        Cita programada = repository.findByEstudianteIdAndEstado(estudiante.id(), Cita.PROGRAMADA);
        if (programada != null) {
            throw ApiException.conflicto("El estudiante ya tiene una cita programada. Reprogramala o cierrala primero");
        }

        List<Alerta> activas = alertaRepository.activasDelEstudiante(estudiante.id());

        // si otro psicorientador lo atiende, no se le puede agendar por encima
        for (Alerta alerta : activas) {
            if (alerta.getPsicorientadorId() != null && !alerta.getPsicorientadorId().equals(yo.id())) {
                throw ApiException.conflicto("A este estudiante lo atiende otro psicorientador. Pide que te lo reasignen");
            }
        }

        Set<String> excluir = new HashSet<>();
        if (request.excluir() != null) {
            excluir.addAll(request.excluir());
        }

        List<Alerta> incluidas = new ArrayList<>();
        for (Alerta alerta : activas) {
            if (!excluir.contains(alerta.getCodigo())) {
                incluidas.add(alerta);
            }
        }

        if (incluidas.isEmpty()) {
            throw ApiException.invalido("La cita debe tratar al menos una alerta activa");
        }

        Cita cita = new Cita();
        cita.setInstitucionId(matricula.getInstitucionId());
        cita.setCodigo(CodigoAleatorio.generar());
        cita.setEstudianteId(estudiante.id());
        cita.setPsicorientadorId(yo.id());
        cita.setMatriculaId(matricula.getId());
        cita.setGrupoId(matricula.getGrupoId());
        cita.setCreadaPor(UsuarioAutenticado.actual().id());
        copiarHorario(cita, request.inicio(), request.duracionMinutos(), request.modalidad(), request.lugar(), request.indicacion());
        guardarSinCruces(cita);

        // agendar tambien es tomar: las activas sin psicorientador quedan con quien agenda
        OffsetDateTime ahora = OffsetDateTime.now();
        for (Alerta alerta : activas) {
            if (alerta.getPsicorientadorId() == null) {
                alerta.setPsicorientadorId(yo.id());
                alerta.setAsignadaEn(ahora);
            }
        }

        for (Alerta alerta : incluidas) {
            alerta.setEstado(Alerta.EN_PROCESO);
            repository.agregarAlerta(cita.getInstitucionId(), cita.getId(), alerta.getId(), estudiante.id());
        }

        return armar(cita, true);
    }

    // ---------------------------------------------------------------- consultar

    @Override
    @Transactional(readOnly = true)
    public CitaResponse buscar(String codigo) {

        TenantSupport.requireTenant(em);

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();
        if (usuario.rol() != Rol.PSICORIENTADOR && usuario.rol() != Rol.ADMIN) {
            throw ApiException.noEncontrado("La cita no existe");
        }

        return armar(obtener(codigo), usuario.rol() == Rol.PSICORIENTADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponse> agenda(LocalDate desde, LocalDate hasta) {

        TenantSupport.requireTenant(em);

        if (hasta.isBefore(desde)) {
            throw ApiException.invalido("La fecha final es antes de la inicial");
        }
        if (hasta.toEpochDay() - desde.toEpochDay() > MAXIMO_DIAS_AGENDA) {
            throw ApiException.invalido("La agenda se consulta maximo por 6 semanas");
        }

        PsicorientadorBasico yo = yo();
        ZoneId zona = ZoneId.systemDefault();
        OffsetDateTime inicio = desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        List<CitaResponse> respuesta = new ArrayList<>();
        for (Cita cita : repository.agenda(yo.id(), inicio, fin)) {
            respuesta.add(armar(cita, true));
        }
        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponse> delEstudiante(Long estudianteId, boolean conObservacion) {

        TenantSupport.requireTenant(em);

        List<CitaResponse> respuesta = new ArrayList<>();
        for (Cita cita : repository.findByEstudianteIdOrderByInicioDesc(estudianteId)) {
            respuesta.add(armar(cita, conObservacion));
        }
        return respuesta;
    }

    // ---------------------------------------------------------------- cerrar

    @Override
    @Transactional
    public CitaResponse iniciar(String codigo) {

        TenantSupport.requireTenant(em);

        Cita cita = miCitaProgramada(codigo);

        if (cita.getIniciadaEn() == null) {
            cita.setIniciadaEn(OffsetDateTime.now());
        }

        return armar(cita, true);
    }

    @Override
    @Transactional
    public CitaResponse finalizar(String codigo, ResultadoCitaRequest request) {

        TenantSupport.requireTenant(em);

        Cita cita = miCitaProgramada(codigo);

        // [id, codigo, categoria, nivel, resultado]
        Map<String, Long> alertasDeLaCita = new HashMap<>();
        for (Object[] fila : repository.alertasDeCita(cita.getId())) {
            alertasDeLaCita.put((String) fila[1], ((Number) fila[0]).longValue());
        }

        Map<String, ResultadoAlerta> resultados = new HashMap<>();
        for (ResultadoAlerta resultado : request.resultados()) {
            if (!alertasDeLaCita.containsKey(resultado.alertaCodigo())) {
                throw ApiException.invalido("Una de las alertas no es de esta cita");
            }
            resultados.put(resultado.alertaCodigo(), resultado);
        }

        if (resultados.size() != alertasDeLaCita.size()) {
            throw ApiException.invalido("Falta el resultado de alguna alerta de la cita");
        }

        OffsetDateTime ahora = OffsetDateTime.now();

        for (ResultadoAlerta resultado : resultados.values()) {
            Long alertaId = alertasDeLaCita.get(resultado.alertaCodigo());
            Alerta alerta = alertaRepository.findById(alertaId).orElseThrow();
            String observacion = resultado.observacion().trim();

            // completada: lo que se escribio es su conclusion
            if (Alerta.COMPLETADA.equals(resultado.resultado())) {
                alerta.setEstado(Alerta.COMPLETADA);
                alerta.setConclusion(observacion);
                alerta.setCompletadaEn(ahora);
            } else {
                alerta.setEstado(Alerta.EN_PROCESO);
            }

            repository.guardarResultado(cita.getId(), alertaId, resultado.resultado(), observacion);
        }

        // si no la habia iniciado, empezo y termino ahora
        if (cita.getIniciadaEn() == null) {
            cita.setIniciadaEn(ahora);
        }
        cita.setEstado(Cita.REALIZADA);
        cita.setCerradaEn(ahora);

        return armar(cita, true);
    }

    @Override
    @Transactional
    public CitaResponse noAsistio(String codigo) {

        TenantSupport.requireTenant(em);

        Cita cita = miCitaProgramada(codigo);

        cita.setEstado(Cita.NO_ASISTIO);
        cita.setCerradaEn(OffsetDateTime.now());

        return armar(cita, true);
    }

    @Override
    @Transactional
    public CitaResponse cancelar(String codigo, CancelarCitaRequest request) {

        TenantSupport.requireTenant(em);

        Cita cita = miCitaProgramada(codigo);

        cita.setEstado(Cita.CANCELADA);
        cita.setMotivoCancelacion(request.motivo().trim());
        cita.setCerradaEn(OffsetDateTime.now());

        return armar(cita, true);
    }

    @Override
    @Transactional
    public CitaResponse reprogramar(String codigo, ReprogramarCitaRequest request) {

        TenantSupport.requireTenant(em);

        Cita cita = miCitaProgramada(codigo);
        copiarHorario(cita, request.inicio(), request.duracionMinutos(), request.modalidad(), request.lugar(), request.indicacion());
        guardarSinCruces(cita);

        return armar(cita, true);
    }

    @Override
    @Transactional
    public void pasarAOtroPsicorientador(Long estudianteId, Long psicorientadorId) {

        TenantSupport.requireTenant(em);

        Cita cita = repository.findByEstudianteIdAndEstado(estudianteId, Cita.PROGRAMADA);

        if (cita == null) {
            return;
        }

        cita.setPsicorientadorId(psicorientadorId);

        try {
            repository.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("El otro psicorientador ya tiene una cita a esa hora. Reprograma la cita primero");
        }
    }

    // ---------------------------------------------------------------- ayudas

    private PsicorientadorBasico yo() {

        PsicorientadorBasico yo = personalService.psicorientadorDeUsuario(UsuarioAutenticado.actual().id());

        if (yo == null) {
            throw ApiException.prohibido("Solo un psicorientador maneja citas");
        }

        return yo;
    }

    private Cita obtener(String codigo) {

        Cita cita = repository.findByCodigo(codigo);

        if (cita == null) {
            throw ApiException.noEncontrado("La cita no existe");
        }

        return cita;
    }

    // solo el psicorientador de la cita la cambia, y solo mientras sigue programada
    private Cita miCitaProgramada(String codigo) {

        PsicorientadorBasico yo = yo();
        Cita cita = obtener(codigo);

        if (!cita.getPsicorientadorId().equals(yo.id())) {
            throw ApiException.prohibido("La cita es de otro psicorientador");
        }

        if (!cita.isProgramada()) {
            throw ApiException.conflicto("La cita ya esta cerrada");
        }

        return cita;
    }

    private void copiarHorario(Cita cita, OffsetDateTime inicio, int duracionMinutos, String modalidad,
            String lugar, String indicacion) {

        cita.setInicio(inicio);
        cita.setFin(inicio.plusMinutes(duracionMinutos));
        cita.setModalidad(modalidad);
        cita.setLugar(vacioANull(lugar));
        cita.setIndicacion(vacioANull(indicacion));
    }

    // la base impide dos citas del mismo psicorientador a la misma hora
    private void guardarSinCruces(Cita cita) {

        try {
            repository.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            String detalle = String.valueOf(e.getMostSpecificCause().getMessage());
            if (detalle.contains("uq_citas_programada_estudiante")) {
                throw ApiException.conflicto("El estudiante ya tiene una cita programada");
            }
            throw ApiException.conflicto("Ya tienes una cita que se cruza con ese horario");
        }
    }

    private CitaResponse armar(Cita cita, boolean conObservacion) {

        Object[] nombres = repository.nombres(cita.getId()).get(0);

        // lo que se hablo de cada alerta es confidencial: solo psicorientadores
        List<AlertaDeCita> alertas = new ArrayList<>();
        for (Object[] fila : repository.alertasDeCita(cita.getId())) {
            String observacion = null;
            if (conObservacion) {
                observacion = (String) fila[5];
            }
            alertas.add(new AlertaDeCita(
                    (String) fila[1], (String) fila[2], (String) fila[3], (String) fila[6], (String) fila[4], observacion));
        }

        return new CitaResponse(
                cita.getCodigo(),
                cita.getEstado(),
                cita.getInicio(),
                cita.getFin(),
                cita.getModalidad(),
                cita.getLugar(),
                cita.getIndicacion(),
                cita.getMotivoCancelacion(),
                cita.getCerradaEn(),
                (String) nombres[0],
                (String) nombres[1],
                (String) nombres[2],
                (String) nombres[3],
                (String) nombres[4],
                nombres[5] + " " + nombres[6],
                cita.getIniciadaEn(),
                cita.isEnCurso(),
                alertas);
    }

    private String vacioANull(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }
}
