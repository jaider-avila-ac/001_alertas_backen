package com.alertas.categoria.repository;

import com.alertas.categoria.model.CategoriaAlerta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface CategoriaAlertaRepository extends JpaRepository<CategoriaAlerta, Long> {

    @Query("SELECT c FROM CategoriaAlerta c ORDER BY lower(c.nombre)")
    List<CategoriaAlerta> todas();

    @Query("SELECT c FROM CategoriaAlerta c WHERE c.activa = true ORDER BY lower(c.nombre)")
    List<CategoriaAlerta> activas();

    long countByActivaTrue();

    // mismo nombre sin importar mayusculas ni tildes (igual que el indice unico)
    @Query(value = "SELECT count(*) FROM categorias_alerta "
            + "WHERE lower(f_unaccent(cat_nombre)) = lower(f_unaccent(:nombre)) AND cat_id <> :sinContar",
            nativeQuery = true)
    long contarConNombre(@Param("nombre") String nombre, @Param("sinContar") Long sinContar);

    // devuelve [id de la categoria, total]
    @Query(value = "SELECT a.ale_cat_id, count(*) FROM alertas a GROUP BY a.ale_cat_id", nativeQuery = true)
    List<Object[]> contarAlertasPorCategoria();

    @Query(value = "SELECT count(*) FROM alertas a WHERE a.ale_cat_id = :categoriaId", nativeQuery = true)
    long contarAlertas(@Param("categoriaId") Long categoriaId);
}
