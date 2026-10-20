package com.alertas.categoria.service;

import com.alertas.categoria.dto.CategoriaResponse;
import java.util.List;

// catalogo comun de categorias de alerta: las mismas 10 para todas las instituciones. no se pagina
public interface CategoriaService {

    // con el total de alertas de cada una en la institucion del contexto
    List<CategoriaResponse> listar();

    // para alertas: falla si no existe
    CategoriaResponse buscar(Long id);
}
