package com.alertas.atencion.service;

import com.alertas.personal.dto.PsicorientadoresInactivadosEvento;

// cuando se inactiva a un psicorientador, sus casos abiertos no se quedan atrapados:
// sus citas programadas se cancelan y sus alertas activas vuelven a la bandeja
public interface LiberacionCasosService {

    void alInactivarPsicorientadores(PsicorientadoresInactivadosEvento evento);
}
