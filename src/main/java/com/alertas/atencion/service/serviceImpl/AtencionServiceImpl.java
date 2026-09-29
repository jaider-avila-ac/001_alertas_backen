package com.alertas.atencion.service.serviceImpl;

import com.alertas.alerta.model.Alerta;
import com.alertas.alerta.repository.AlertaExpedienteFila;
import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.alerta.repository.BandejaFila;
import com.alertas.alerta.repository.EstudianteAtencionFila;
import com.alertas.atencion.dto.AlertaExpedienteResponse;
import com.alertas.atencion.dto.BandejaResponse;
import com.alertas.atencion.dto.EstudianteAtencionResponse;
import com.alertas.atencion.dto.ExpedienteResponse;
import com.alertas.atencion.dto.PsicorientadorResponse;
import com.alertas.atencion.dto.ReasignarRequest;
import com.alertas.atencion.dto.SeguimientoResponse;
import com.alertas.atencion.service.AtencionService;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.cita.dto.CitaResponse;
import com.alertas.cita.service.CitaService;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.matricula.dto.MatriculaResponse;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.notificacion.service.NotificacionService;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.valoracion.service.ValoracionService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtencionServiceImpl implements AtencionService {

    private static final int TAMANIO_MAXIMO = 50;
    private static final String[] NIVELES = {null, "LEVE", "MODERADO", "ALTO", "CRITICO"};
    private static final Set<String> PESTANAS = Set.of("POR_AGENDAR", "CON_CITA", "EN_CURSO", "HISTORIAL");

    private final AlertaRepository alertaRepository;
    private final EstudianteService estudianteService;
    private final MatriculaService matriculaService;
    private final PersonalService personalService;
    private final CitaService citaService;
    private final BitacoraService bitacoraService;
    private final NotificacionService notificacionService;
    private final ValoracionService valoracionService;
    private final EntityManager em;

    public AtencionServiceImpl(
            AlertaRepository alertaRepository,
            EstudianteService estudianteService,
            MatriculaService matriculaService,
            PersonalService personalService,
            CitaService citaService,
            BitacoraService bitacoraService,
            NotificacionService notificacionService,
            ValoracionService valoracionService,
            EntityManager em) {

        this.alertaRepository = alertaRepository;
        this.estudianteService = estudianteService;
        this.matriculaService = matriculaService;
        this.personalService = personalService;
        this.citaService = citaService;
        this.bitacoraService = bitacoraService;
        this.notificacionService = notificacionService;
        this.valoracionService = valoracionService;
        this.em = em;
    }

    // ---------------------------------------------------------------- bandeja y tomar

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BandejaResponse> bandeja(int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<BandejaFila> page = alertaRepository.bandeja(pageRequest);

        List<BandejaResponse> contenido = new ArrayList<>();
        for (BandejaFila fila : page.getContent()) {
            contenido.add(new BandejaResponse(
                    fila.getCodigo(),
                    fila.getNombres(),
                    fila.getApellidos(),
                    fila.getGradoNombre(),
                    fila.getGrupoNombre(),
                    fila.getAlertas(),
                    nivelTexto(fila.getNivelMaximo()),
                    Boolean.TRUE.equals(fila.getPrioritaria()),
                    Boolean.TRUE.equals(fila.getPidioAyuda()),
                    Boolean.TRUE.equals(fila.getVeniaEnAtencion()),
                    fila.getDesde()));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional
    public ExpedienteResponse tomar(String estudianteCodigo) {

        TenantSupport.requireTenant(em);

        PsicorientadorBasico yo = yo();
        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(estudianteCodigo);

        // un solo update: si dos lo toman a la vez, el segundo no encuentra alertas libres
        int tomadas = alertaRepository.tomar(estudiante.id(), yo.id());

        if (tomadas == 0) {
            List<Alerta> activas = alertaRepository.activasDelEstudiante(estudiante.id());

            if (activas.isEmpty()) {
                throw ApiException.conflicto("El estudiante no tiene alertas activas");
            }

            for (Alerta alerta : activas) {
                if (alerta.getPsicorientadorId() != null && !alerta.getPsicorientadorId().equals(yo.id())) {
                    throw ApiException.conflicto("Otro psicorientador ya tomo a este estudiante");
                }
            }
        }

        return armarExpediente(estudiante, true);
    }

    // ---------------------------------------------------------------- mis estudiantes y expediente

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EstudianteAtencionResponse> misEstudiantes(
            String pestana, String nivel, Long categoriaId, Long gradoId, Long grupoId, int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        if (!PESTANAS.contains(pestana)) {
            throw ApiException.invalido("La pestana no es valida");
        }

        PsicorientadorBasico yo = yo();
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<EstudianteAtencionFila> page = alertaRepository.misEstudiantes(
                yo.id(), pestana, nivel, categoriaId, gradoId, grupoId, pageRequest);

        List<EstudianteAtencionResponse> contenido = new ArrayList<>();
        for (EstudianteAtencionFila fila : page.getContent()) {
            contenido.add(new EstudianteAtencionResponse(
                    fila.getCodigo(),
                    fila.getNombres(),
                    fila.getApellidos(),
                    fila.getGradoNombre(),
                    fila.getGrupoNombre(),
                    fila.getActivas(),
                    nivelTexto(fila.getNivelMaximo()),
                    Boolean.TRUE.equals(fila.getPrioritaria()),
                    fila.getCitaCodigo(),
                    fila.getCitaInicio(),
                    fila.getCitaFin()));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpedienteResponse expediente(String estudianteCodigo) {

        TenantSupport.requireTenant(em);

        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(estudianteCodigo);
        boolean esPsicorientador = UsuarioAutenticado.actual().rol() == Rol.PSICORIENTADOR;

        return armarExpediente(estudiante, esPsicorientador);
    }

    // ---------------------------------------------------------------- cambios en alertas

    @Override
    @Transactional
    public ExpedienteResponse cambiarNivel(String alertaCodigo, String nivel) {

        TenantSupport.requireTenant(em);

        PsicorientadorBasico yo = yo();
        Alerta alerta = obtenerAlerta(alertaCodigo);

        if (Alerta.COMPLETADA.equals(alerta.getEstado())) {
            throw ApiException.conflicto("La alerta esta completada. Reabrela para cambiarla");
        }

        if (alerta.getPsicorientadorId() == null || !alerta.getPsicorientadorId().equals(yo.id())) {
            throw ApiException.prohibido("Toma primero al estudiante para cambiar sus alertas");
        }

        alerta.setNivel(nivel);
        alertaRepository.saveAndFlush(alerta);

        return armarExpediente(estudianteDe(alerta), true);
    }

    @Override
    @Transactional
    public ExpedienteResponse reabrir(String alertaCodigo) {

        TenantSupport.requireTenant(em);

        PsicorientadorBasico yo = yo();
        Alerta alerta = obtenerAlerta(alertaCodigo);

        if (!Alerta.COMPLETADA.equals(alerta.getEstado())) {
            throw ApiException.conflicto("Solo se reabre una alerta completada");
        }

        // si otro psicorientador atiende hoy al estudiante, la alerta es de el
        for (Alerta activa : alertaRepository.activasDelEstudiante(alerta.getEstudianteId())) {
            if (activa.getPsicorientadorId() != null && !activa.getPsicorientadorId().equals(yo.id())) {
                throw ApiException.conflicto("A este estudiante lo atiende otro psicorientador");
            }
        }

        alerta.setEstado(Alerta.EN_PROCESO);
        alerta.setCompletadaEn(null);
        alerta.setPsicorientadorId(yo.id());
        alerta.setAsignadaEn(OffsetDateTime.now());
        alertaRepository.saveAndFlush(alerta);

        // si ya hay cita programada, entra en ella
        List<Object[]> cita = alertaRepository.citaProgramada(alerta.getEstudianteId());
        if (!cita.isEmpty()) {
            Long citaId = ((Number) cita.get(0)[0]).longValue();
            alertaRepository.pegarACita(alerta.getInstitucionId(), citaId, alerta.getId(), alerta.getEstudianteId());
        }

        bitacoraService.registrar("REABRIR_ALERTA", "alerta", alerta.getId(), null);
        return armarExpediente(estudianteDe(alerta), true);
    }

    @Override
    @Transactional
    public ExpedienteResponse reasignar(String estudianteCodigo, ReasignarRequest request) {

        TenantSupport.requireTenant(em);

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();
        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(estudianteCodigo);
        List<Alerta> activas = alertaRepository.activasDelEstudiante(estudiante.id());

        if (activas.isEmpty()) {
            throw ApiException.conflicto("El estudiante no tiene alertas activas para reasignar");
        }

        // el psicorientador solo reasigna lo suyo; el admin cualquier estudiante
        Long actualId = null;
        for (Alerta alerta : activas) {
            if (alerta.getPsicorientadorId() != null) {
                actualId = alerta.getPsicorientadorId();
            }
        }

        if (usuario.rol() == Rol.PSICORIENTADOR) {
            PsicorientadorBasico yo = yo();
            if (actualId == null || !actualId.equals(yo.id())) {
                throw ApiException.prohibido("Solo puedes reasignar estudiantes que atiendes");
            }
        }

        PsicorientadorBasico nuevo = personalService.psicorientadorActivo(request.psicorientadorCodigo());

        if (nuevo.id().equals(actualId)) {
            throw ApiException.invalido("El estudiante ya esta con " + nuevo.nombreCompleto());
        }

        alertaRepository.asignar(estudiante.id(), nuevo.id());
        citaService.pasarAOtroPsicorientador(estudiante.id(), nuevo.id());

        bitacoraService.registrar("REASIGNAR", "estudiante", estudiante.id(),
                "a " + nuevo.nombreCompleto() + ": " + request.motivo().trim());

        notificacionService.notificar(nuevo.usuarioId(), "CASO_ASIGNADO", "Te asignaron un caso",
                "Ahora atiendes a " + estudiante.nombreCompleto(), "/atencion/estudiantes/" + estudiante.codigo());

        return armarExpediente(estudiante, usuario.rol() == Rol.PSICORIENTADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PsicorientadorResponse> psicorientadores() {

        TenantSupport.requireTenant(em);

        List<PsicorientadorResponse> respuesta = new ArrayList<>();
        for (PsicorientadorBasico psicorientador : personalService.psicorientadoresActivos()) {
            respuesta.add(new PsicorientadorResponse(psicorientador.codigo(), psicorientador.nombreCompleto()));
        }
        return respuesta;
    }

    // ---------------------------------------------------------------- ayudas

    private PsicorientadorBasico yo() {

        PsicorientadorBasico yo = personalService.psicorientadorDeUsuario(UsuarioAutenticado.actual().id());

        if (yo == null) {
            throw ApiException.prohibido("Esto lo hace un psicorientador");
        }

        return yo;
    }

    private Alerta obtenerAlerta(String codigo) {

        Alerta alerta = alertaRepository.findByCodigo(codigo);

        if (alerta == null) {
            throw ApiException.noEncontrado("La alerta no existe");
        }

        return alerta;
    }

    private EstudianteBasico estudianteDe(Alerta alerta) {
        return estudianteService.basicoPorId(alerta.getEstudianteId());
    }

    private ExpedienteResponse armarExpediente(EstudianteBasico estudiante, boolean conObservaciones) {

        PsicorientadorBasico yo = null;
        if (UsuarioAutenticado.actual().rol() == Rol.PSICORIENTADOR) {
            yo = personalService.psicorientadorDeUsuario(UsuarioAutenticado.actual().id());
        }

        // grado y grupo del anio activo
        String grado = null;
        String grupo = null;
        for (MatriculaResponse matricula : matriculaService.trayectoria(estudiante.id())) {
            if (matricula.anioActivo()) {
                grado = matricula.gradoNombre();
                grupo = matricula.grupoNombre();
            }
        }

        // lo escrito en cada cita, por alerta. confidencial: solo psicorientadores
        List<Object[]> seguimientos = new ArrayList<>();
        if (conObservaciones) {
            seguimientos = alertaRepository.seguimientosDelEstudiante(estudiante.id());
        }

        List<AlertaExpedienteResponse> alertas = new ArrayList<>();
        for (AlertaExpedienteFila fila : alertaRepository.delEstudiante(estudiante.id())) {
            List<SeguimientoResponse> suyos = new ArrayList<>();
            for (Object[] seguimiento : seguimientos) {
                if (fila.getCodigo().equals(seguimiento[0])) {
                    suyos.add(new SeguimientoResponse(
                            aInstant(seguimiento[1]),
                            (String) seguimiento[2],
                            (String) seguimiento[3]));
                }
            }
            AlertaExpedienteResponse alerta = AlertaExpedienteResponse.desde(fila, suyos);
            // la conclusion tambien es lo que se escribio en la cita: solo psicorientadores
            if (!conObservaciones) {
                alerta = alerta.sinConclusion();
            }
            alertas.add(alerta);
        }

        // quien lo atiende: el de sus alertas activas
        String psicorientador = null;
        boolean esMio = false;
        boolean sinTomar = false;
        for (Alerta activa : alertaRepository.activasDelEstudiante(estudiante.id())) {
            if (activa.getPsicorientadorId() == null) {
                sinTomar = true;
            } else if (yo != null && activa.getPsicorientadorId().equals(yo.id())) {
                esMio = true;
            }
        }
        for (AlertaExpedienteResponse alerta : alertas) {
            if (!Alerta.COMPLETADA.equals(alerta.estado()) && alerta.psicorientador() != null) {
                psicorientador = alerta.psicorientador();
            }
        }

        List<CitaResponse> citas = citaService.delEstudiante(estudiante.id(), conObservaciones);
        String programada = null;
        for (CitaResponse cita : citas) {
            if ("PROGRAMADA".equals(cita.estado())) {
                programada = cita.codigo();
            }
        }

        return new ExpedienteResponse(
                estudiante.codigo(),
                estudiante.nombres(),
                estudiante.apellidos(),
                grado,
                grupo,
                estudiante.activo(),
                psicorientador,
                esMio,
                sinTomar,
                programada,
                alertas,
                citas,
                valoracionService.delEstudiante(estudiante.id(), conObservaciones));
    }

    // segun el driver, una fecha de sql nativo llega como Instant, OffsetDateTime o Timestamp
    private Instant aInstant(Object valor) {

        if (valor instanceof Instant) {
            return (Instant) valor;
        }
        if (valor instanceof OffsetDateTime) {
            return ((OffsetDateTime) valor).toInstant();
        }
        if (valor instanceof Timestamp) {
            return ((Timestamp) valor).toInstant();
        }
        return null;
    }

    private String nivelTexto(Integer nivel) {

        if (nivel == null || nivel < 1 || nivel > 4) {
            return null;
        }

        return NIVELES[nivel];
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
}
