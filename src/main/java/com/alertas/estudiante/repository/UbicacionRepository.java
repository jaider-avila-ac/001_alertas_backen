package com.alertas.estudiante.repository;

import com.alertas.estudiante.model.Ubicacion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    Ubicacion findByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    List<Ubicacion> findByEstudianteIdInAndAnioId(Collection<Long> estudianteIds, Long anioId);

    // historial del estudiante: un grupo por anio, del mas reciente al mas viejo.
    // devuelve [anio, grado, grupo, id del grupo]
    @Query(value = """
            SELECT a.anl_anio, gr.gra_nombre, g.grp_nombre, g.grp_id
            FROM ubicaciones u
            JOIN anios_lectivos a ON a.anl_id = u.ubi_anl_id
            JOIN grupos g ON g.grp_id = u.ubi_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE u.ubi_est_id = :estudianteId
            ORDER BY a.anl_anio DESC
            """, nativeQuery = true)
    List<Object[]> historial(@Param("estudianteId") Long estudianteId);
}
