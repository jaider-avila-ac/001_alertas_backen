package com.alertas.estructura.repository;

import com.alertas.estructura.model.AnioLectivo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnioLectivoRepository extends JpaRepository<AnioLectivo, Long> {

    List<AnioLectivo> findAllByOrderByAnioDesc();

    Optional<AnioLectivo> findByActivoTrue();

    boolean existsByAnio(int anio);

    // la bd solo deja un anio activo (indice unico parcial). primero se apagan todos y se manda
    // a la bd antes de prender el nuevo, si no el indice falla en la mitad del cambio
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AnioLectivo a SET a.activo = false WHERE a.activo = true")
    int desactivarTodos();
}
