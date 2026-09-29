package com.alertas.alerta.repository;

import java.time.Instant;

// un estudiante de la bandeja: sus alertas pendientes que nadie ha tomado.
// nivelMaximo: 1 leve, 2 moderado, 3 alto, 4 critico
public interface BandejaFila {

    String getCodigo();

    String getNombres();

    String getApellidos();

    String getGradoNombre();

    String getGrupoNombre();

    Long getAlertas();

    Integer getNivelMaximo();

    Boolean getPrioritaria();

    Boolean getPidioAyuda();

    // alguna estaba en proceso: la atendia un psicorientador que se inactivo
    Boolean getVeniaEnAtencion();

    Instant getDesde();
}
