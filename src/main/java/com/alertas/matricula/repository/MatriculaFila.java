package com.alertas.matricula.repository;

import java.time.LocalDate;

// una fila de la trayectoria del estudiante
public interface MatriculaFila {

    Long getId();

    Long getAnioId();

    Integer getAnio();

    Boolean getAnioActivo();

    Long getGrupoId();

    String getGrupoNombre();

    String getGradoNombre();

    String getEstado();

    String getOrigen();

    LocalDate getFechaMatricula();

    LocalDate getFechaCierre();

    String getMotivoCierre();
}
