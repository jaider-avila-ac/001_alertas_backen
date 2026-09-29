package com.alertas.alerta.repository;

import java.time.Instant;
import java.time.LocalDate;

// una alerta completa para el expediente del psicorientador
public interface AlertaExpedienteFila {

    String getCodigo();

    String getOrigen();

    String getEstado();

    String getNivel();

    Boolean getPrioritaria();

    Boolean getPeligroInmediato();

    String getCategoria();

    String getDescripcion();

    LocalDate getFechaHecho();

    String getLugar();

    String getHorarioSeguro();

    String getModalidadPreferida();

    Boolean getAutorizaSmsFamiliares();

    String getGradoNombre();

    String getGrupoNombre();

    Integer getAnio();

    String getReportadaPor();

    String getPsicorientador();

    String getConclusion();

    Instant getCreadoEn();

    Instant getCompletadaEn();
}
