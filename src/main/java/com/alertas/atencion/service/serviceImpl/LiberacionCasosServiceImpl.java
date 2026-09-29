package com.alertas.atencion.service.serviceImpl;

import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.atencion.service.LiberacionCasosService;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.cita.repository.CitaRepository;
import com.alertas.notificacion.service.NotificacionService;
import com.alertas.personal.dto.PsicorientadorBasico;
import com.alertas.personal.dto.PsicorientadoresInactivadosEvento;
import com.alertas.personal.service.PersonalService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiberacionCasosServiceImpl implements LiberacionCasosService {

    private static final String MOTIVO = "El psicorientador ya no esta disponible";

    private final AlertaRepository alertaRepository;
    private final CitaRepository citaRepository;
    private final PersonalService personalService;
    private final NotificacionService notificacionService;
    private final BitacoraService bitacoraService;

    public LiberacionCasosServiceImpl(
            AlertaRepository alertaRepository,
            CitaRepository citaRepository,
            PersonalService personalService,
            NotificacionService notificacionService,
            BitacoraService bitacoraService) {

        this.alertaRepository = alertaRepository;
        this.citaRepository = citaRepository;
        this.personalService = personalService;
        this.notificacionService = notificacionService;
        this.bitacoraService = bitacoraService;
    }

    // corre en la misma transaccion en la que se inactivo: o pasa todo o no pasa nada
    @Override
    @EventListener
    @Transactional
    public void alInactivarPsicorientadores(PsicorientadoresInactivadosEvento evento) {

        // antes de cancelar: a quien hay que avisarle
        List<Long> estudiantes = citaRepository.estudiantesConCitaDeInactivos();

        int citas = citaRepository.cancelarDeInactivos(MOTIVO);
        int alertas = alertaRepository.liberarDeInactivos();

        if (citas == 0 && alertas == 0) {
            return;
        }

        bitacoraService.registrar("LIBERAR_CASOS", "institucion", evento.institucionId(),
                alertas + " alertas volvieron a la bandeja y " + citas + " citas se cancelaron");

        notificacionService.notificarVarios(estudiantes, "CITA_CANCELADA", "Tu cita fue cancelada",
                "Orientacion te dara una nueva fecha", "/mi-proceso");

        if (alertas > 0) {
            List<Long> psicorientadores = new ArrayList<>();
            for (PsicorientadorBasico psicorientador : personalService.psicorientadoresActivos()) {
                psicorientadores.add(psicorientador.usuarioId());
            }
            notificacionService.notificarVarios(psicorientadores, "CASOS_EN_BANDEJA", "Hay casos en la bandeja",
                    "Volvieron casos de un psicorientador que ya no esta disponible", "/atencion/bandeja");
        }
    }
}
