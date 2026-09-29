package com.alertas.valoracion.repository;

import com.alertas.valoracion.model.Valoracion;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    // estudiantes activos matriculados en el anio activo (no retirados) con su ultima valoracion.
    // filtro: TODOS, POR_VALORAR (nunca o hace mas de :dias) o NUNCA. primero los que llevan mas tiempo sin valorar
    @Query(value = """
            WITH ultimas AS (
                SELECT DISTINCT ON (v.val_est_id) v.val_est_id, v.val_creado_en, v.val_psi_id
                FROM valoraciones v
                ORDER BY v.val_est_id, v.val_creado_en DESC
            )
            SELECT e.est_codigo AS codigo, e.est_nombres AS nombres, e.est_apellidos AS apellidos,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre,
                   ul.val_creado_en AS ultima, p.per_nombres || ' ' || p.per_apellidos AS ultimaPor,
                   (ul.val_creado_en IS NULL OR ul.val_creado_en < now() - make_interval(days => :dias)) AS porValorar
            FROM matriculas m
            JOIN anios_lectivos an ON an.anl_id = m.mat_anl_id AND an.anl_activo
            JOIN estudiantes e ON e.est_id = m.mat_est_id
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            JOIN grupos g ON g.grp_id = m.mat_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            LEFT JOIN ultimas ul ON ul.val_est_id = e.est_id
            LEFT JOIN personal p ON p.per_id = ul.val_psi_id
            WHERE u.usu_activo AND m.mat_estado <> 'RETIRADA'
              AND (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
              AND (:filtro = 'TODOS'
                   OR (:filtro = 'NUNCA' AND ul.val_creado_en IS NULL)
                   OR (:filtro = 'POR_VALORAR'
                       AND (ul.val_creado_en IS NULL OR ul.val_creado_en < now() - make_interval(days => :dias))))
            ORDER BY ul.val_creado_en NULLS FIRST, gr.gra_orden, g.grp_nombre, e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            WITH ultimas AS (
                SELECT v.val_est_id, max(v.val_creado_en) AS val_creado_en
                FROM valoraciones v
                GROUP BY v.val_est_id
            )
            SELECT count(*)
            FROM matriculas m
            JOIN anios_lectivos an ON an.anl_id = m.mat_anl_id AND an.anl_activo
            JOIN estudiantes e ON e.est_id = m.mat_est_id
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            JOIN grupos g ON g.grp_id = m.mat_grp_id
            LEFT JOIN ultimas ul ON ul.val_est_id = e.est_id
            WHERE u.usu_activo AND m.mat_estado <> 'RETIRADA'
              AND (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
              AND (:filtro = 'TODOS'
                   OR (:filtro = 'NUNCA' AND ul.val_creado_en IS NULL)
                   OR (:filtro = 'POR_VALORAR'
                       AND (ul.val_creado_en IS NULL OR ul.val_creado_en < now() - make_interval(days => :dias))))
            """,
            nativeQuery = true)
    Page<EstudianteValoracionFila> estudiantes(
            @Param("filtro") String filtro,
            @Param("dias") int dias,
            @Param("texto") String texto,
            @Param("gradoId") Long gradoId,
            @Param("grupoId") Long grupoId,
            Pageable pageable);

    // historial del estudiante, de la mas nueva a la mas vieja, con el grado y grupo que tenia
    @Query(value = """
            SELECT v.val_codigo AS codigo, v.val_creado_en AS fecha,
                   p.per_nombres || ' ' || p.per_apellidos AS psicorientador, v.val_observacion AS observacion,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre, an.anl_anio AS anio
            FROM valoraciones v
            JOIN personal p ON p.per_id = v.val_psi_id
            JOIN matriculas m ON m.mat_id = v.val_mat_id
            JOIN anios_lectivos an ON an.anl_id = m.mat_anl_id
            JOIN grupos g ON g.grp_id = m.mat_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE v.val_est_id = :estudianteId
            ORDER BY v.val_creado_en DESC
            """, nativeQuery = true)
    List<ValoracionFila> delEstudiante(@Param("estudianteId") Long estudianteId);
}
