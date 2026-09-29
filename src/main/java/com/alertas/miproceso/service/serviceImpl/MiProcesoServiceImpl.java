package com.alertas.miproceso.service.serviceImpl;

import com.alertas.alerta.repository.AlertaExpedienteFila;
import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.cita.dto.CitaResponse;
import com.alertas.cita.service.CitaService;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.miproceso.dto.MiProcesoResponse;
import com.alertas.miproceso.service.MiProcesoService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiProcesoServiceImpl implements MiProcesoService {

    private final EstudianteService estudianteService;
    private final AlertaRepository alertaRepository;
    private final CitaService citaService;
    private final EntityManager em;

    public MiProcesoServiceImpl(
            EstudianteService estudianteService,
            AlertaRepository alertaRepository,
            CitaService citaService,
            EntityManager em) {

        this.estudianteService = estudianteService;
        this.alertaRepository = alertaRepository;
        this.citaService = citaService;
        this.em = em;
    }

    // un estudiante tiene pocas alertas y citas, por eso no se pagina
    @Override
    @Transactional(readOnly = true)
    public MiProcesoResponse miProceso() {

        TenantSupport.requireTenant(em);

        EstudianteBasico estudiante = estudianteService.basicoPorUsuario(UsuarioAutenticado.actual().id());

        if (estudiante == null) {
            throw ApiException.prohibido("Solo el estudiante ve su proceso");
        }

        List<MiProcesoResponse.Alerta> alertas = new ArrayList<>();
        List<MiProcesoResponse.Solicitud> solicitudes = new ArrayList<>();
        String psicorientador = null;

        for (AlertaExpedienteFila fila : alertaRepository.delEstudiante(estudiante.id())) {
            if ("ESTUDIANTE".equals(fila.getOrigen())) {
                solicitudes.add(new MiProcesoResponse.Solicitud(
                        fila.getEstado(), fila.getCreadoEn(), fila.getCategoria(), fila.getDescripcion()));
            } else {
                alertas.add(new MiProcesoResponse.Alerta(fila.getEstado(), fila.getCreadoEn()));
            }

            // quien lo atiende hoy: el de sus alertas activas
            if (!"COMPLETADA".equals(fila.getEstado()) && fila.getPsicorientador() != null) {
                psicorientador = fila.getPsicorientador();
            }
        }

        // de las citas solo cuando, donde y la indicacion; nada de lo que se hablo
        List<MiProcesoResponse.Cita> citas = new ArrayList<>();
        for (CitaResponse cita : citaService.delEstudiante(estudiante.id(), false)) {
            citas.add(new MiProcesoResponse.Cita(
                    cita.estado(),
                    cita.inicio(),
                    cita.fin(),
                    cita.modalidad(),
                    cita.lugar(),
                    cita.indicacion(),
                    cita.psicorientador()));
        }

        return new MiProcesoResponse(psicorientador, alertas, solicitudes, citas);
    }
}
