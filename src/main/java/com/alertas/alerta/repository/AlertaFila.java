package com.alertas.alerta.repository;

import java.time.Instant;
import java.time.LocalDate;

// una fila de "mis alertas reportadas". el grupo es el que tenia el estudiante ese dia
public interface AlertaFila {

    String getCodigo();

    String getOrigen();

    String getEstudianteCodigo();

    String getEstudianteNombres();

    String getEstudianteApellidos();

    String getGrupo();

    String getCategoria();

    String getNivel();

    String getEstado();

    Boolean getPeligroInmediato();

    Boolean getPrioritaria();

    LocalDate getFechaHecho();

    Instant getCreadoEn();
}
