package com.alertas.matricula.repository;

import java.time.Instant;

public interface MovimientoFila {

    Long getMatriculaId();

    Instant getFecha();

    String getMotivo();

    String getGradoAnterior();

    String getGrupoAnterior();

    String getGradoNuevo();

    String getGrupoNuevo();
}
