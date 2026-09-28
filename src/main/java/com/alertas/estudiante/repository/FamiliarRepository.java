package com.alertas.estudiante.repository;

import com.alertas.estudiante.model.Familiar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamiliarRepository extends JpaRepository<Familiar, Long> {

    List<Familiar> findByEstudianteIdOrderByPosicionAsc(Long estudianteId);

    Familiar findByEstudianteIdAndPosicion(Long estudianteId, int posicion);

    // se borran de una y se mandan a la bd antes de guardar los nuevos, si no la posicion choca
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Familiar f WHERE f.estudiante.id = :estudianteId")
    void borrarDelEstudiante(@Param("estudianteId") Long estudianteId);
}
