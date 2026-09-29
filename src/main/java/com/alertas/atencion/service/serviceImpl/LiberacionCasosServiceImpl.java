package com.alertas.atencion.service.serviceImpl;

import com.alertas.alerta.repository.AlertaRepository;
import com.alertas.atencion.service.LiberacionCasosService;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.cita.repository.CitaRepository;
import com.alertas.personal.dto.PsicorientadoresInactivadosEvento;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiberacionCasosServiceImpl implements LiberacionCasosService {

    private static final String MOTIVO = "El psicorientador ya no esta disponible";

    private final AlertaRepository alertaRepository;
    private final CitaRepository citaRepository;
    private final BitacoraService bitacoraService;

    public LiberacionCasosServiceImpl(
            AlertaRepository alertaRepository,
            CitaRepository citaRepository,
            BitacoraService bitacoraService) {

        this.alertaRepository = alertaRepository;
        this.citaRepository = citaRepository;
        this.bitacoraService = bitacoraService;
    }

    // corre en la misma transaccion en la que se inactivo: o pasa todo o no pasa nada
    @Override
    @EventListener
    @Transactional
    public void alInactivarPsicorientadores(PsicorientadoresInactivadosEvento evento) {

        int citas = citaRepository.cancelarDeInactivos(MOTIVO);
        int alertas = alertaRepository.liberarDeInactivos();

        if (citas > 0 || alertas > 0) {
            bitacoraService.registrar("LIBERAR_CASOS", "institucion", evento.institucionId(),
                    alertas + " alertas volvieron a la bandeja y " + citas + " citas se cancelaron");
        }
    }
}
