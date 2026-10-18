package com.alertas.categoria.service;

import com.alertas.categoria.dto.CategoriaResponse;
import java.util.List;

// categorias de alerta de la institucion del contexto. son pocas, no se pagina
public interface CategoriaService {

    // las de una institucion nueva. son fijas: las mismas para todas y siempre activas
    void crearPredeterminadas();

    // con el total de alertas de cada una
    List<CategoriaResponse> listar();

    // para alertas: falla si no existe o es una vieja que ya no se usa
    CategoriaResponse buscarActiva(Long id);
}
