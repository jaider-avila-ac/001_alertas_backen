package com.alertas.valoracion.repository;

import com.alertas.shared.RangoEdad;
import com.alertas.valoracion.model.Valoracion;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    // estudiantes activos con su ultima valoracion.
    // filtro: TODOS, POR_VALORAR (nunca o hace mas de :dias) o NUNCA. primero los que llevan mas tiempo sin valorar
    @Query(value = """
            WITH ultimas AS (
                SELECT DISTINCT ON (v.val_est_id) v.val_est_id, v.val_creado_en, v.val_psi_id
                FROM valoraciones v
                ORDER BY v.val_est_id, v.val_creado_en DESC
            )
            SELECT e.est_codigo AS codigo, e.est_nombres AS nombres, e.est_apellidos AS apellidos,
                   e.est_grupo AS grupo,
                   ul.val_creado_en AS ultima, p.per_nombres || ' ' || p.per_apellidos AS ultimaPor,
                   (ul.val_creado_en IS NULL OR ul.val_creado_en < now() - make_interval(days => :dias)) AS porValorar
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            LEFT JOIN ultimas ul ON ul.val_est_id = e.est_id
            LEFT JOIN personal p ON p.per_id = ul.val_psi_id
            WHERE u.usu_activo
              AND (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
              AND (:filtro = 'TODOS'
                   OR (:filtro = 'NUNCA' AND ul.val_creado_en IS NULL)
                   OR (:filtro = 'POR_VALORAR'
                       AND (ul.val_creado_en IS NULL OR ul.val_creado_en < now() - make_interval(days => :dias))))
            ORDER BY ul.val_creado_en NULLS FIRST, e.est_grupo, e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            WITH ultimas AS (
                SELECT v.val_est_id, max(v.val_creado_en) AS val_creado_en
                FROM valoraciones v
                GROUP BY v.val_est_id
            )
            SELECT count(*)
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            LEFT JOIN ultimas ul ON ul.val_est_id = e.est_id
            WHERE u.usu_activo
              AND (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
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
            @Param("grupo") String grupo,
            @Param("edad") String edad,
            Pageable pageable);

    // historial del estudiante, de la mas nueva a la mas vieja
    @Query(value = """
            SELECT v.val_codigo AS codigo, v.val_creado_en AS fecha,
                   p.per_nombres || ' ' || p.per_apellidos AS psicorientador, v.val_observacion AS observacion
            FROM valoraciones v
            JOIN personal p ON p.per_id = v.val_psi_id
            WHERE v.val_est_id = :estudianteId
            ORDER BY v.val_creado_en DESC
            """, nativeQuery = true)
    List<ValoracionFila> delEstudiante(@Param("estudianteId") Long estudianteId);
}
