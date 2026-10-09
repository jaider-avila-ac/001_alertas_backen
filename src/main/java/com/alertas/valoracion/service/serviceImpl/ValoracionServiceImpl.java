package com.alertas.valoracion.service.serviceImpl;

import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.matricula.model.Matricula;
import com.alertas.matricula.service.MatriculaService;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.CodigoAleatorio;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.alertas.valoracion.dto.EstudianteValoracionResponse;
import com.alertas.valoracion.dto.RegistrarValoracionRequest;
import com.alertas.valoracion.dto.ValoracionResponse;
import com.alertas.valoracion.dto.ValoracionesConfigResponse;
import com.alertas.valoracion.model.Valoracion;
import com.alertas.valoracion.repository.EstudianteValoracionFila;
import com.alertas.valoracion.repository.ValoracionFila;
import com.alertas.valoracion.repository.ValoracionRepository;
import com.alertas.valoracion.service.ValoracionService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValoracionServiceImpl implements ValoracionService {

    private static final int TAMANIO_MAXIMO = 50;
    private static final Set<String> FILTROS = Set.of("TODOS", "POR_VALORAR", "NUNCA");

    private final ValoracionRepository repository;
    private final InstitucionService institucionService;
    private final EstudianteService estudianteService;
    private final MatriculaService matriculaService;
    private final PersonalService personalService;
    private final EntityManager em;

    public ValoracionServiceImpl(
            ValoracionRepository repository,
            InstitucionService institucionService,
            EstudianteService estudianteService,
            MatriculaService matriculaService,
            PersonalService personalService,
            EntityManager em) {

        this.repository = repository;
        this.institucionService = institucionService;
        this.estudianteService = estudianteService;
        this.matriculaService = matriculaService;
        this.personalService = personalService;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public ValoracionesConfigResponse configuracion() {

        Long institucionId = TenantSupport.requireTenant(em);

        return new ValoracionesConfigResponse(
                institucionService.valoracionesActivas(institucionId),
                institucionService.valoracionesDias(institucionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EstudianteValoracionResponse> estudiantes(
            String filtro, String texto, Long gradoId, Long grupoId, int pagina, int tamanio) {

        Long institucionId = TenantSupport.requireTenant(em);
        exigirActivas(institucionId);

        if (!FILTROS.contains(filtro)) {
            throw ApiException.invalido("El filtro no es valido");
        }

        String busqueda = null;
        if (texto != null && !texto.isBlank()) {
            busqueda = texto.trim();
        }

        int dias = institucionService.valoracionesDias(institucionId);
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        Page<EstudianteValoracionFila> page = repository.estudiantes(filtro, dias, busqueda, gradoId, grupoId, pageRequest);

        List<EstudianteValoracionResponse> contenido = new ArrayList<>();
        for (EstudianteValoracionFila fila : page.getContent()) {
            contenido.add(new EstudianteValoracionResponse(
                    fila.getCodigo(),
                    fila.getNombres(),
                    fila.getApellidos(),
                    fila.getGradoNombre(),
                    fila.getGrupoNombre(),
                    fila.getUltima(),
                    fila.getUltimaPor(),
                    Boolean.TRUE.equals(fila.getPorValorar())));
        }

        return PageResponse.de(contenido, page);
    }

    @Override
    @Transactional
    public ValoracionResponse registrar(RegistrarValoracionRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        exigirActivas(institucionId);

        PsicorientadorBasico yo = personalService.psicorientadorDeUsuario(UsuarioAutenticado.actual().id());
        if (yo == null) {
            throw ApiException.prohibido("Las valoraciones las registra un psicorientador");
        }

        EstudianteBasico estudiante = estudianteService.basicoPorCodigo(request.estudianteCodigo());
        if (!estudiante.activo()) {
            throw ApiException.conflicto(estudiante.nombreCompleto() + " esta inactivo");
        }

        Matricula matricula = matriculaService.delAnioActivo(estudiante.id());
        if (matricula == null || Matricula.RETIRADA.equals(matricula.getEstado())) {
            throw ApiException.conflicto(estudiante.nombreCompleto()
                    + " no tiene matricula en el año activo. El administrador debe asignarle un grupo");
        }

        Valoracion valoracion = new Valoracion();
        valoracion.setInstitucionId(institucionId);
        valoracion.setCodigo(CodigoAleatorio.generar());
        valoracion.setEstudianteId(estudiante.id());
        valoracion.setMatriculaId(matricula.getId());
        valoracion.setPsicorientadorId(yo.id());
        valoracion.setObservacion(request.observacion().trim());
        repository.saveAndFlush(valoracion);

        // se devuelve igual que en el historial
        for (ValoracionFila fila : repository.delEstudiante(estudiante.id())) {
            if (fila.getCodigo().equals(valoracion.getCodigo())) {
                return ValoracionResponse.desde(fila, true);
            }
        }
        throw new IllegalStateException("No se encontro la valoracion recien guardada");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValoracionResponse> delEstudiante(Long estudianteId, boolean conObservacion) {

        List<ValoracionResponse> respuesta = new ArrayList<>();
        for (ValoracionFila fila : repository.delEstudiante(estudianteId)) {
            respuesta.add(ValoracionResponse.desde(fila, conObservacion));
        }
        return respuesta;
    }

    // ---------------------------------------------------------------- ayudas

    private void exigirActivas(Long institucionId) {

        if (!institucionService.valoracionesActivas(institucionId)) {
            throw ApiException.conflicto("Las valoraciones de rutina no estan activas en la institucion");
        }
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
