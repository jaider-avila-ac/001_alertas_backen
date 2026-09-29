package com.alertas.alerta.repository;

import java.time.Instant;

// un estudiante en "mis estudiantes" del psicorientador. la cita es la programada (si tiene)
public interface EstudianteAtencionFila {

    String getCodigo();

    String getNombres();

    String getApellidos();

    String getGradoNombre();

    String getGrupoNombre();

    Long getActivas();

    Integer getNivelMaximo();

    Boolean getPrioritaria();

    String getCitaCodigo();

    Instant getCitaInicio();

    Instant getCitaFin();
}
