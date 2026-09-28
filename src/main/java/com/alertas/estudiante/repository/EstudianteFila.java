package com.alertas.estudiante.repository;

// una fila del listado: el estudiante con su grado y grupo del anio que se consulta.
// spring llena esta interfaz con los alias de la consulta
public interface EstudianteFila {

    String getCodigo();

    String getTipoDoc();

    String getNroDoc();

    String getNombres();

    String getApellidos();

    Boolean getActivo();

    Long getGrupoId();

    String getGradoNombre();

    Integer getGradoOrden();

    String getGrupoNombre();
}
