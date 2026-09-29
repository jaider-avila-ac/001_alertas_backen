package com.alertas.categoria.service;

import com.alertas.categoria.dto.CategoriaResponse;
import java.util.List;

// categorias de alerta de la institucion del contexto. son pocas, no se pagina
public interface CategoriaService {

    // las categorias con las que arranca una institucion nueva
    void crearPredeterminadas();

    // soloActivas: lo que ve el docente al crear una alerta
    List<CategoriaResponse> listar(boolean soloActivas);

    CategoriaResponse crear(String nombre);

    CategoriaResponse renombrar(Long id, String nombre);

    // no se puede apagar la ultima activa
    CategoriaResponse cambiarEstado(Long id, boolean activa);

    // solo si no tiene alertas; si tiene, se desactiva
    void borrar(Long id);

    // para alertas: falla si no existe o esta desactivada
    CategoriaResponse buscarActiva(Long id);
}
