package com.alertas.estructura.repository;

import com.alertas.estructura.model.Grupo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    @Query("SELECT g FROM Grupo g JOIN FETCH g.grado JOIN FETCH g.anio "
            + "WHERE g.anio.id = :anioId ORDER BY g.grado.orden, g.nombre")
    List<Grupo> buscarPorAnio(@Param("anioId") Long anioId);

    @Query("SELECT count(g) > 0 FROM Grupo g WHERE g.anio.id = :anioId AND g.grado.id = :gradoId "
            + "AND lower(g.nombre) = lower(:nombre) AND (:excluirId IS NULL OR g.id <> :excluirId)")
    boolean existeNombre(
            @Param("anioId") Long anioId,
            @Param("gradoId") Long gradoId,
            @Param("nombre") String nombre,
            @Param("excluirId") Long excluirId);

    long countByAnioId(Long anioId);

    // grupos de un grado en el anio activo o en anios que vienen
    @Query("SELECT count(g) FROM Grupo g WHERE g.grado.id = :gradoId AND g.anio.anio >= :desdeAnio")
    long contarDelGradoDesde(@Param("gradoId") Long gradoId, @Param("desdeAnio") int desdeAnio);

    // estudiantes por grupo. ubicaciones todavia no tiene entidad (llega con estudiantes),
    // por eso va en sql. devuelve [id del grupo, total]
    @Query(value = "SELECT u.ubi_grp_id, count(*) FROM ubicaciones u WHERE u.ubi_anl_id = :anioId GROUP BY u.ubi_grp_id",
            nativeQuery = true)
    List<Object[]> contarEstudiantesPorGrupo(@Param("anioId") Long anioId);

    @Query(value = "SELECT count(*) FROM ubicaciones u WHERE u.ubi_grp_id = :grupoId", nativeQuery = true)
    long contarEstudiantes(@Param("grupoId") Long grupoId);

    @Query(value = "SELECT count(*) FROM alertas a WHERE a.ale_grp_id = :grupoId", nativeQuery = true)
    long contarAlertas(@Param("grupoId") Long grupoId);
}
