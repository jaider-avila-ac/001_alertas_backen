package com.alertas.matricula.repository;

import com.alertas.matricula.model.Matricula;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    Matricula findByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    List<Matricula> findByEstudianteIdInAndAnioId(Collection<Long> estudianteIds, Long anioId);

    List<Matricula> findByEstudianteIdAndEstado(Long estudianteId, String estado);

    List<Matricula> findByAnioIdAndGrupoId(Long anioId, Long grupoId);

    // la del anio anterior mas cercano. sirve para saber si paso de grado o repite
    @Query(value = """
            SELECT m.* FROM matriculas m
            JOIN anios_lectivos a ON a.anl_id = m.mat_anl_id
            WHERE m.mat_est_id = :estudianteId AND a.anl_anio < :anio
            ORDER BY a.anl_anio DESC
            LIMIT 1
            """, nativeQuery = true)
    Matricula buscarAnterior(@Param("estudianteId") Long estudianteId, @Param("anio") int anio);

    // trayectoria del estudiante, del anio mas reciente al mas viejo
    @Query(value = """
            SELECT m.mat_id AS id, a.anl_id AS anioId, a.anl_anio AS anio, a.anl_activo AS anioActivo,
                   g.grp_id AS grupoId, g.grp_nombre AS grupoNombre, gr.gra_nombre AS gradoNombre,
                   m.mat_estado AS estado, m.mat_origen AS origen,
                   m.mat_fecha_matricula AS fechaMatricula, m.mat_fecha_cierre AS fechaCierre,
                   m.mat_motivo_cierre AS motivoCierre
            FROM matriculas m
            JOIN anios_lectivos a ON a.anl_id = m.mat_anl_id
            JOIN grupos g ON g.grp_id = m.mat_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE m.mat_est_id = :estudianteId
            ORDER BY a.anl_anio DESC
            """, nativeQuery = true)
    List<MatriculaFila> trayectoria(@Param("estudianteId") Long estudianteId);

    // movimientos de todas las matriculas del estudiante, en orden
    @Query(value = """
            SELECT mv.mov_mat_id AS matriculaId, mv.mov_creado_en AS fecha, mv.mov_motivo AS motivo,
                   ga.gra_nombre AS gradoAnterior, a.grp_nombre AS grupoAnterior,
                   gn.gra_nombre AS gradoNuevo, n.grp_nombre AS grupoNuevo
            FROM matricula_movimientos mv
            JOIN matriculas m ON m.mat_id = mv.mov_mat_id
            JOIN grupos a ON a.grp_id = mv.mov_grp_anterior_id
            JOIN grados ga ON ga.gra_id = a.grp_gra_id
            JOIN grupos n ON n.grp_id = mv.mov_grp_nuevo_id
            JOIN grados gn ON gn.gra_id = n.grp_gra_id
            WHERE m.mat_est_id = :estudianteId
            ORDER BY mv.mov_creado_en
            """, nativeQuery = true)
    List<MovimientoFila> movimientos(@Param("estudianteId") Long estudianteId);

    // ---- para la promocion ----

    // matriculas activas del grupo cuyo usuario esta activo (los inactivos no pasan)
    // y que todavia no tienen matricula en el anio destino
    @Query(value = """
            SELECT m.* FROM matriculas m
            JOIN estudiantes e ON e.est_id = m.mat_est_id
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE m.mat_grp_id = :grupoId AND m.mat_estado = 'ACTIVA' AND u.usu_activo
              AND NOT EXISTS (SELECT 1 FROM matriculas d WHERE d.mat_est_id = m.mat_est_id AND d.mat_anl_id = :destinoId)
            """, nativeQuery = true)
    List<Matricula> porPromover(@Param("grupoId") Long grupoId, @Param("destinoId") Long destinoId);

    // por grupo del anio: [grupo, activos que pueden pasar, ya tienen matricula en el anio destino, graduados]
    @Query(value = """
            SELECT m.mat_grp_id,
                   count(*) FILTER (WHERE m.mat_estado = 'ACTIVA' AND u.usu_activo
                                    AND NOT EXISTS (SELECT 1 FROM matriculas d
                                                    WHERE d.mat_est_id = m.mat_est_id AND d.mat_anl_id = :destinoId)),
                   count(*) FILTER (WHERE EXISTS (SELECT 1 FROM matriculas d
                                                  WHERE d.mat_est_id = m.mat_est_id AND d.mat_anl_id = :destinoId)),
                   count(*) FILTER (WHERE m.mat_estado = 'GRADUADA')
            FROM matriculas m
            JOIN estudiantes e ON e.est_id = m.mat_est_id
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE m.mat_anl_id = :origenId
            GROUP BY m.mat_grp_id
            """, nativeQuery = true)
    List<Object[]> resumenPromocion(@Param("origenId") Long origenId, @Param("destinoId") Long destinoId);
}
