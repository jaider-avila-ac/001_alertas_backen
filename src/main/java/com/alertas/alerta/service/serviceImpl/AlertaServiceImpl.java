package com.alertas.alerta.service.serviceImpl;

import com.alertas.alerta.dto.AlertaDetalleResponse;
import com.alertas.alerta.dto.AlertaFilaResponse;
import com.alertas.alerta.dto.CrearAlertaRequest;
import com.alertas.alerta.dto.SolicitudAyudaRequest;
import com.alertas.alerta.model.Alerta;
import com.alertas.alerta.repository.AlertaFila;
import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.alerta.service.AlertaService;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.categoria.service.CategoriaService;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.matricula.model.Matricula;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.notificacion.service.NotificacionService;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertaServiceImpl implements AlertaService {

    private static final int TAMANIO_MAXIMO = 50;

    // urgencia del estudiante -> nivel de la alerta
    private static final Map<String, String> NIVEL_POR_URGENCIA = Map.of(
            "BAJA", "LEVE",
            "MEDIA", "MODERADO",
            "ALTA", "ALTO");

    private final AlertaRepository repository;
    private final EstudianteService estudianteService;
    private final MatriculaService matriculaService;
    private final CategoriaService categoriaService;
    private final PersonalService personalService;
    private final NotificacionService notificacionService;
    private final EntityManager em;

    public AlertaServiceImpl(
            AlertaRepository repository,
            EstudianteService estudianteService,
            MatriculaService matriculaService,
            CategoriaService categoriaService,
            PersonalService personalService,
            NotificacionService notificacionService,
            EntityManager em) {

        this.repository = repository;
        this.estudianteService = estudianteService;
        this.matriculaService = matriculaService;
        this.categoriaService = categoriaService;
        this.personalService = personalService;
        this.notificacionService = notificacionService;
        this.em = em;
    }

    // ---------------------------------------------------------------- crear

    @Override
    @Transactional
    public AlertaDetalleResponse crear(CrearAlertaRequest request) {

        TenantSupport.requireTenant(em);

        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(request.estudianteCodigo());

        if (!estudiante.activo()) {
            throw ApiException.conflicto(estudiante.nombres() + " " + estudiante.apellidos()
                    + " esta inactivo, no se le pueden crear alertas");
        }

        Alerta alerta = nueva(estudiante, request.categoriaId(), Alerta.DOCENTE);
        alerta.setNivel(request.nivel());
        alerta.setDescripcion(request.descripcion().trim());
        alerta.setFechaHecho(request.fechaHecho());
        alerta.setLugar(vacioANull(request.lugar()));
        alerta.setPeligroInmediato(request.peligroInmediato());

        repository.save(alerta);
        pegarACitaProgramada(alerta);

        AlertaDetalleResponse respuesta = detalle(alerta, true);
        avisarPsicorientadores(alerta, respuesta);
        return respuesta;
    }

    @Override
    @Transactional
    public AlertaDetalleResponse solicitarAyuda(SolicitudAyudaRequest request) {

        TenantSupport.requireTenant(em);

        EstudianteBasico estudiante = estudianteService.basicoPorUsuario(UsuarioAutenticado.actual().id());

        if (estudiante == null) {
            throw ApiException.prohibido("Solo un estudiante puede pedir ayuda");
        }

        Alerta alerta = nueva(estudiante, request.categoriaId(), Alerta.ESTUDIANTE);
        alerta.setNivel(NIVEL_POR_URGENCIA.get(request.urgencia()));
        alerta.setDescripcion(request.descripcion().trim());
        alerta.setHorarioSeguro(vacioANull(request.horarioSeguro()));
        alerta.setModalidadPreferida(vacioANull(request.modalidadPreferida()));
        // por defecto no se le avisa a la familia: el estudiante decide
        alerta.setAutorizaSmsFamiliares(request.autorizaSmsFamiliares());

        repository.save(alerta);
        pegarACitaProgramada(alerta);

        AlertaDetalleResponse respuesta = detalle(alerta, false);
        avisarPsicorientadores(alerta, respuesta);
        return respuesta;
    }

    // lo comun a las dos: matricula del momento, categoria activa y psicorientador si ya lo tiene
    private Alerta nueva(EstudianteBasico estudiante, Long categoriaId, String origen) {

        Matricula matricula = matriculaService.delAnioActivo(estudiante.id());

        if (matricula == null || Matricula.RETIRADA.equals(matricula.getEstado())) {
            throw ApiException.conflicto(estudiante.nombres() + " " + estudiante.apellidos()
                    + " no tiene matricula en el anio activo. El administrador debe asignarle un grupo");
        }

        categoriaService.buscarActiva(categoriaId);

        Alerta alerta = new Alerta();
        alerta.setInstitucionId(matricula.getInstitucionId());
        alerta.setCodigo(CodigoAleatorio.generar());
        alerta.setEstudianteId(estudiante.id());
        alerta.setOrigen(origen);
        alerta.setReportadaPor(UsuarioAutenticado.actual().id());
        alerta.setCategoriaId(categoriaId);
        alerta.setAnioId(matricula.getAnioId());
        alerta.setGrupoId(matricula.getGrupoId());
        alerta.setMatriculaId(matricula.getId());
        alerta.setEstado(Alerta.PENDIENTE);

        // si ya hay un psicorientador atendiendo al estudiante, la alerta nueva le llega a el
        List<Long> psicorientador = repository.psicorientadorActual(estudiante.id());
        if (!psicorientador.isEmpty()) {
            alerta.setPsicorientadorId(psicorientador.get(0));
            alerta.setAsignadaEn(OffsetDateTime.now());
        }

        return alerta;
    }

    // si el estudiante ya tiene una cita programada, la alerta nueva entra en esa cita y queda en proceso
    private void pegarACitaProgramada(Alerta alerta) {

        List<Object[]> cita = repository.citaProgramada(alerta.getEstudianteId());

        if (cita.isEmpty()) {
            return;
        }

        Long citaId = ((Number) cita.get(0)[0]).longValue();
        Long psicorientadorId = ((Number) cita.get(0)[1]).longValue();

        alerta.setEstado(Alerta.EN_PROCESO);
        alerta.setPsicorientadorId(psicorientadorId);
        if (alerta.getAsignadaEn() == null) {
            alerta.setAsignadaEn(OffsetDateTime.now());
        }
        repository.saveAndFlush(alerta);

        repository.pegarACita(alerta.getInstitucionId(), citaId, alerta.getId(), alerta.getEstudianteId());
    }

    // al psicorientador que ya lo atiende; si nadie lo atiende, a todos los activos (esta en la bandeja)
    private void avisarPsicorientadores(Alerta alerta, AlertaDetalleResponse respuesta) {

        String tipo = "ALERTA_NUEVA";
        String titulo = "Nueva alerta";
        if (Alerta.ESTUDIANTE.equals(alerta.getOrigen())) {
            tipo = "SOLICITUD_AYUDA";
            titulo = "Un estudiante pidio ayuda";
        }
        if (respuesta.prioritaria()) {
            tipo = "ALERTA_PRIORITARIA";
            titulo = "Alerta prioritaria";
        }

        String mensaje = respuesta.estudianteNombres() + " " + respuesta.estudianteApellidos() + " (" + respuesta.gradoNombre()
                + " " + respuesta.grupoNombre() + ") · " + respuesta.categoria();
        String enlace = "/atencion/estudiantes/" + respuesta.estudianteCodigo();

        if (alerta.getPsicorientadorId() != null) {
            PsicorientadorBasico psicorientador = personalService.psicorientadorPorId(alerta.getPsicorientadorId());
            if (psicorientador != null) {
                notificacionService.notificar(psicorientador.usuarioId(), tipo, titulo, mensaje, enlace);
            }
            return;
        }

        List<Long> usuarios = new ArrayList<>();
        for (PsicorientadorBasico psicorientador : personalService.psicorientadoresActivos()) {
            usuarios.add(psicorientador.usuarioId());
        }
        notificacionService.notificarVarios(usuarios, tipo, titulo, mensaje, enlace);
    }

    // ---------------------------------------------------------------- consultar

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertaFilaResponse> misReportadas(String estado, int pagina, int tamanio) {

        TenantSupport.requireTenant(em);

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<AlertaFila> page = repository.reportadasPor(UsuarioAutenticado.actual().id(), estado, pageRequest);

        List<AlertaFilaResponse> contenido = new ArrayList<>();
        for (AlertaFila fila : page.getContent()) {
            contenido.add(AlertaFilaResponse.desde(fila));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertaDetalleResponse buscar(String codigo) {

        TenantSupport.requireTenant(em);

        UsuarioAutenticado usuario = UsuarioAutenticado.actual();
        Alerta alerta = repository.findByCodigo(codigo);

        boolean atiende = usuario.rol() == Rol.PSICORIENTADOR || usuario.rol() == Rol.ADMIN;

        // a quien no la creo ni la atiende, la alerta "no existe"
        if (alerta == null || (!atiende && !alerta.getReportadaPor().equals(usuario.id()))) {
            throw ApiException.noEncontrado("La alerta no existe");
        }

        return detalle(alerta, atiende);
    }

    // ---------------------------------------------------------------- ayudas

    private AlertaDetalleResponse detalle(Alerta alerta, boolean conConclusion) {

        // tras el insert se vuelve a leer para tener el valor de prioritaria que calcula la base
        em.flush();
        em.refresh(alerta);

        Object[] nombres = repository.nombresDelDetalle(alerta.getId()).get(0);

        String psicorientador = null;
        if (nombres[6] != null) {
            psicorientador = nombres[6] + " " + nombres[7];
        }

        String conclusion = null;
        if (conConclusion) {
            conclusion = alerta.getConclusion();
        }

        return new AlertaDetalleResponse(
                alerta.getCodigo(),
                alerta.getOrigen(),
                alerta.getEstado(),
                alerta.getNivel(),
                Boolean.TRUE.equals(alerta.getPrioritaria()),
                alerta.isPeligroInmediato(),
                (String) nombres[0],
                alerta.getDescripcion(),
                alerta.getFechaHecho(),
                alerta.getLugar(),
                alerta.getHorarioSeguro(),
                alerta.getModalidadPreferida(),
                alerta.getAutorizaSmsFamiliares(),
                (String) nombres[8],
                (String) nombres[9],
                (String) nombres[10],
                (String) nombres[1],
                (String) nombres[2],
                ((Number) nombres[3]).intValue(),
                nombres[4] + " " + nombres[5],
                psicorientador,
                conclusion,
                alerta.getCreadoEn());
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
