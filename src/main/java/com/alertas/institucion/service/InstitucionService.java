package com.alertas.institucion.service;

import com.alertas.institucion.dto.EstadoInstitucion;

public interface InstitucionService {

    // null si no existe
    EstadoInstitucion estadoPorSlug(String slug);

    EstadoInstitucion estadoPorId(Long id);

    // llamar despues de cambiar slug, activa o acceso de estudiantes
    void olvidarCache(Long id, String slug);
}
