package com.alertas.alerta.repository;

import java.time.Instant;
import java.time.LocalDate;

// una fila de "mis alertas reportadas". grado y grupo son los del momento de la alerta
public interface AlertaFila {

    String getCodigo();

    String getOrigen();

    String getEstudianteCodigo();

    String getEstudianteNombres();

    String getEstudianteApellidos();

    String getGradoNombre();

    String getGrupoNombre();

    Integer getAnio();

    String getCategoria();

    String getNivel();

    String getEstado();

    Boolean getPeligroInmediato();

    Boolean getPrioritaria();

    LocalDate getFechaHecho();

    Instant getCreadoEn();
}
