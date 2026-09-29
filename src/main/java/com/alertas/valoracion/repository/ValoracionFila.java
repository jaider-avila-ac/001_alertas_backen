package com.alertas.valoracion.repository;

import java.time.Instant;

public interface ValoracionFila {

    String getCodigo();

    Instant getFecha();

    String getPsicorientador();

    String getObservacion();

    String getGradoNombre();

    String getGrupoNombre();

    Integer getAnio();
}
