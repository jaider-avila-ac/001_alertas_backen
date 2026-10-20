package com.alertas.categoria.repository;

import com.alertas.categoria.model.CategoriaAlerta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// el catalogo es comun; los totales de alertas si los filtra RLS por institucion
public interface CategoriaAlertaRepository extends JpaRepository<CategoriaAlerta, Long> {

    @Query("SELECT c FROM CategoriaAlerta c ORDER BY lower(c.nombre)")
    List<CategoriaAlerta> todas();

    // devuelve [id de la categoria, total]
    @Query(value = "SELECT a.ale_cat_id, count(*) FROM alertas a GROUP BY a.ale_cat_id", nativeQuery = true)
    List<Object[]> contarAlertasPorCategoria();
}
