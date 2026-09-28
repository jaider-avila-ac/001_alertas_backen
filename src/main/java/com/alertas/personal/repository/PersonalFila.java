package com.alertas.personal.repository;

// una fila del listado de docentes y psicorientadores. spring la llena con los alias de la consulta
public interface PersonalFila {

    String getCodigo();

    String getTipoDoc();

    String getNroDoc();

    String getNombres();

    String getApellidos();

    String getCorreo();

    String getRol();

    Boolean getActivo();
}
