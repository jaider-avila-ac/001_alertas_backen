package com.alertas.estudiante.repository;

import java.time.LocalDate;

// una fila del listado. spring llena esta interfaz con los alias de la consulta
public interface EstudianteFila {

    String getCodigo();

    String getTipoDoc();

    String getNroDoc();

    String getNombres();

    String getApellidos();

    Boolean getActivo();

    String getGrupo();

    LocalDate getFechaNacimiento();
}
