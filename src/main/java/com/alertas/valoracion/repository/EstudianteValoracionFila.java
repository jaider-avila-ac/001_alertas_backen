package com.alertas.valoracion.repository;

import java.time.Instant;

// un estudiante del anio activo con su ultima valoracion (null si nunca)
public interface EstudianteValoracionFila {

    String getCodigo();

    String getNombres();

    String getApellidos();

    String getGradoNombre();

    String getGrupoNombre();

    Instant getUltima();

    String getUltimaPor();

    Boolean getPorValorar();
}
