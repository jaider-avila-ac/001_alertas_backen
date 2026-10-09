package com.alertas.estadistica.repository;

import com.alertas.estadistica.dto.FiltroEstadistica;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

// toda la pagina de estadisticas sale de DOS consultas (mas la que fija el colegio para RLS):
//  1. alertas: un CTE con las alertas filtradas y de ahi todos los conteos, en un solo json
//  2. citas, valoraciones y las listas para los filtros (anios, grados, grupos, categorias), en otro json
// solo conteos en la base, nunca se cargan entidades. las fechas van en la zona del colegio
@Repository
public class EstadisticaRepository {

    // sin anio en el filtro se usa el activo; con "todos" no se filtra por anio
    private static final String ANIO = "coalesce(CAST(:anioId AS bigint), (SELECT anl_id FROM anios_lectivos WHERE anl_activo))";

    private static final String CONSULTA_ALERTAS = """
            WITH base AS (
                SELECT a.ale_id, a.ale_estado AS estado, a.ale_nivel AS nivel, a.ale_prioritaria AS prioritaria,
                       a.ale_est_id AS est_id, a.ale_psi_id AS psi_id, a.ale_creado_en AS creado,
                       CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date) AS dia,
                       c.cat_nombre AS categoria, gr.gra_nombre AS grado, gr.gra_orden AS orden, g.grp_nombre AS grupo,
                       an.anl_anio AS anio, coalesce(CAST(e.est_genero AS text), 'N') AS genero,
                       e.est_fecha_nacimiento AS nacimiento,
                       CASE WHEN a.ale_origen = 'ESTUDIANTE' THEN 'ESTUDIANTE' ELSE u.usu_rol END AS origen
                FROM alertas a
                JOIN grupos g ON g.grp_id = a.ale_grp_id
                JOIN grados gr ON gr.gra_id = g.grp_gra_id
                JOIN anios_lectivos an ON an.anl_id = g.grp_anl_id
                JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
                JOIN estudiantes e ON e.est_id = a.ale_est_id
                JOIN usuarios u ON u.usu_id = a.ale_reportada_por
                WHERE (CAST(:todos AS boolean) OR a.ale_anl_id = %s)
                  AND (CAST(:desde AS date) IS NULL
                       OR CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL
                       OR CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                  AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
                  AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
                  AND (CAST(:categoriaId AS bigint) IS NULL OR a.ale_cat_id = CAST(:categoriaId AS bigint))
            )
            SELECT CAST(json_build_object(
                'estados', (SELECT json_build_array(count(*),
                                                    count(*) FILTER (WHERE estado = 'PENDIENTE'),
                                                    count(*) FILTER (WHERE estado = 'EN_PROCESO'),
                                                    count(*) FILTER (WHERE estado = 'COMPLETADA'),
                                                    count(*) FILTER (WHERE prioritaria),
                                                    count(DISTINCT est_id))
                            FROM base),
                'primeraCita', (SELECT json_build_array(avg(GREATEST(EXTRACT(EPOCH FROM (pc.primera - b.creado)), 0) / 3600.0),
                                                        count(*))
                                FROM base b
                                JOIN LATERAL (
                                    SELECT min(ci.cit_inicio) AS primera
                                    FROM citas_alertas ca
                                    JOIN citas ci ON ci.cit_id = ca.cia_cit_id
                                    WHERE ca.cia_ale_id = b.ale_id AND ci.cit_estado <> 'CANCELADA'
                                ) pc ON pc.primera IS NOT NULL),
                'porMes', (SELECT coalesce(json_agg(json_build_array(mes, n) ORDER BY mes), CAST('[]' AS json))
                           FROM (SELECT to_char(dia, 'YYYY-MM') AS mes, count(*) AS n FROM base GROUP BY 1) x),
                'porCategoria', (SELECT coalesce(json_agg(json_build_array(categoria, n) ORDER BY n DESC, categoria), CAST('[]' AS json))
                                 FROM (SELECT categoria, count(*) AS n FROM base GROUP BY categoria) x),
                'porNivel', (SELECT coalesce(json_agg(json_build_array(nivel, n)), CAST('[]' AS json))
                             FROM (SELECT nivel, count(*) AS n FROM base GROUP BY nivel) x),
                'porGrupo', (SELECT coalesce(json_agg(json_build_array(grado, grupo, anio, n) ORDER BY anio, orden, grupo), CAST('[]' AS json))
                             FROM (SELECT grado, grupo, anio, orden, count(*) AS n FROM base GROUP BY grado, grupo, anio, orden) x),
                'porGenero', (SELECT coalesce(json_agg(json_build_array(genero, n)), CAST('[]' AS json))
                              FROM (SELECT genero, count(*) AS n FROM base GROUP BY genero) x),
                'porEdad', (SELECT coalesce(json_agg(json_build_array(rango, n)), CAST('[]' AS json))
                            FROM (SELECT CASE
                                             WHEN nacimiento IS NULL THEN 'N'
                                             WHEN extract(year FROM age(dia, nacimiento)) <= 8 THEN 'HASTA_8'
                                             WHEN extract(year FROM age(dia, nacimiento)) <= 11 THEN 'DE_9_A_11'
                                             WHEN extract(year FROM age(dia, nacimiento)) <= 14 THEN 'DE_12_A_14'
                                             WHEN extract(year FROM age(dia, nacimiento)) <= 17 THEN 'DE_15_A_17'
                                             ELSE 'DESDE_18'
                                         END AS rango,
                                         count(*) AS n
                                  FROM base GROUP BY 1) x),
                'porOrigen', (SELECT coalesce(json_agg(json_build_array(origen, n)), CAST('[]' AS json))
                              FROM (SELECT origen, count(*) AS n FROM base GROUP BY origen) x),
                'porPsicorientador', (SELECT coalesce(json_agg(json_build_array(nombre, atendidas, completadas)
                                                               ORDER BY atendidas DESC, nombre), CAST('[]' AS json))
                                      FROM (SELECT p.per_nombres || ' ' || p.per_apellidos AS nombre, count(*) AS atendidas,
                                                   count(*) FILTER (WHERE b.estado = 'COMPLETADA') AS completadas
                                            FROM base b JOIN personal p ON p.per_id = b.psi_id
                                            GROUP BY p.per_id, p.per_nombres, p.per_apellidos) x)
            ) AS text)
            """.formatted(ANIO);

    private static final String CONSULTA_CITAS_Y_FILTROS = """
            SELECT CAST(json_build_object(
                'citas', (SELECT json_build_array(count(*) FILTER (WHERE c.cit_estado = 'REALIZADA'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'NO_ASISTIO'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'CANCELADA'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'PROGRAMADA'))
                          FROM citas c
                          JOIN grupos g ON g.grp_id = c.cit_grp_id
                          WHERE (CAST(:todos AS boolean) OR g.grp_anl_id = %1$s)
                            AND (CAST(:desde AS date) IS NULL
                                 OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                            AND (CAST(:hasta AS date) IS NULL
                                 OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                            AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
                            AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
                            AND (CAST(:categoriaId AS bigint) IS NULL OR EXISTS (
                                  SELECT 1 FROM citas_alertas ca JOIN alertas a ON a.ale_id = ca.cia_ale_id
                                  WHERE ca.cia_cit_id = c.cit_id AND a.ale_cat_id = CAST(:categoriaId AS bigint)))),
                'valoraciones', (SELECT json_build_array(count(*), count(DISTINCT v.val_est_id))
                                 FROM valoraciones v
                                 JOIN matriculas m ON m.mat_id = v.val_mat_id
                                 JOIN grupos g ON g.grp_id = m.mat_grp_id
                                 WHERE (CAST(:todos AS boolean) OR m.mat_anl_id = %1$s)
                                   AND (CAST(:desde AS date) IS NULL
                                        OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                                   AND (CAST(:hasta AS date) IS NULL
                                        OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                                   AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
                                   AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))),
                'anioAplicado', (SELECT CASE WHEN CAST(:todos AS boolean) THEN NULL ELSE %1$s END),
                'anios', (SELECT coalesce(json_agg(json_build_array(anl_id, anl_anio, anl_activo) ORDER BY anl_anio DESC), CAST('[]' AS json))
                          FROM anios_lectivos),
                'grados', (SELECT coalesce(json_agg(json_build_array(gra_id, gra_nombre) ORDER BY gra_orden), CAST('[]' AS json))
                           FROM grados WHERE gra_activo),
                'grupos', (SELECT coalesce(json_agg(json_build_array(g.grp_id, g.grp_nombre, g.grp_gra_id)
                                                    ORDER BY gr.gra_orden, g.grp_nombre), CAST('[]' AS json))
                           FROM grupos g JOIN grados gr ON gr.gra_id = g.grp_gra_id
                           WHERE g.grp_anl_id = %1$s),
                'categorias', (SELECT coalesce(json_agg(json_build_array(cat_id, cat_nombre) ORDER BY cat_nombre), CAST('[]' AS json))
                               FROM categorias_alerta)
            ) AS text)
            """.formatted(ANIO);

    private final EntityManager em;

    public EstadisticaRepository(EntityManager em) {
        this.em = em;
    }

    // json con todos los conteos de las alertas (ver CONSULTA_ALERTAS)
    public String alertas(FiltroEstadistica filtro, String zona) {
        return (String) consulta(CONSULTA_ALERTAS, filtro, zona).getSingleResult();
    }

    // json con citas, valoraciones, el anio que se aplico y las listas de los filtros
    public String citasYFiltros(FiltroEstadistica filtro, String zona) {
        return (String) consulta(CONSULTA_CITAS_Y_FILTROS, filtro, zona).getSingleResult();
    }

    // ---------------------------------------------------------------- ayudas

    private Query consulta(String sql, FiltroEstadistica filtro, String zona) {

        Query query = em.createNativeQuery(sql);
        query.setParameter("anioId", filtro.anioId());
        query.setParameter("todos", filtro.todosLosAnios());
        query.setParameter("desde", fecha(filtro.desde()));
        query.setParameter("hasta", fecha(filtro.hasta()));
        query.setParameter("gradoId", filtro.gradoId());
        query.setParameter("grupoId", filtro.grupoId());
        query.setParameter("categoriaId", filtro.categoriaId());
        query.setParameter("zona", zona);
        return query;
    }

    // como texto: asi postgres recibe el null con tipo y el CAST lo vuelve fecha
    private String fecha(LocalDate fecha) {

        if (fecha == null) {
            return null;
        }
        return fecha.toString();
    }
}
