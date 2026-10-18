package com.alertas.estadistica.repository;

import com.alertas.estadistica.dto.FiltroEstadistica;
import com.alertas.shared.RangoEdad;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

// toda la pagina de estadisticas sale de DOS consultas (mas la que fija el colegio para RLS):
//  1. alertas: un CTE con las alertas filtradas y de ahi todos los conteos, en un solo json
//  2. citas, valoraciones y las listas para los filtros (anios, grupos, categorias), en otro json
// solo conteos en la base, nunca se cargan entidades. las fechas van en la zona del colegio.
// el anio es el del calendario; el grupo de la alerta es el que tenia el estudiante ese dia
@Repository
public class EstadisticaRepository {

    private static final String DIA_ALERTA = "CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date)";

    private static final String CONSULTA_ALERTAS = """
            WITH todas AS (
                SELECT a.ale_id, a.ale_estado AS estado, a.ale_nivel AS nivel, a.ale_prioritaria AS prioritaria,
                       a.ale_est_id AS est_id, a.ale_psi_id AS psi_id, a.ale_creado_en AS creado,
                       %1$s AS dia,
                       c.cat_nombre AS categoria, coalesce(a.ale_grupo, 'Sin grupo') AS grupo,
                       coalesce(CAST(e.est_genero AS text), 'N') AS genero,
                       %2$s AS rango,
                       CASE WHEN a.ale_origen = 'ESTUDIANTE' THEN 'ESTUDIANTE' ELSE u.usu_rol END AS origen
                FROM alertas a
                JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
                JOIN estudiantes e ON e.est_id = a.ale_est_id
                JOIN usuarios u ON u.usu_id = a.ale_reportada_por
                WHERE (CAST(:anio AS integer) IS NULL OR extract(year FROM %1$s) = CAST(:anio AS integer))
                  AND (CAST(:desde AS date) IS NULL OR %1$s >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL OR %1$s <= CAST(:hasta AS date))
                  AND (CAST(:grupo AS text) IS NULL OR lower(a.ale_grupo) = lower(CAST(:grupo AS text)))
                  AND (CAST(:categoriaId AS bigint) IS NULL OR a.ale_cat_id = CAST(:categoriaId AS bigint))
            ),
            base AS (
                SELECT * FROM todas WHERE CAST(:edad AS text) IS NULL OR rango = CAST(:edad AS text)
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
                'porGrupo', (SELECT coalesce(json_agg(json_build_array(grupo, n) ORDER BY n DESC, grupo), CAST('[]' AS json))
                             FROM (SELECT grupo, count(*) AS n FROM base GROUP BY grupo) x),
                'porGenero', (SELECT coalesce(json_agg(json_build_array(genero, n)), CAST('[]' AS json))
                              FROM (SELECT genero, count(*) AS n FROM base GROUP BY genero) x),
                'porEdad', (SELECT coalesce(json_agg(json_build_array(rango, n)), CAST('[]' AS json))
                            FROM (SELECT rango, count(*) AS n FROM base GROUP BY rango) x),
                'porOrigen', (SELECT coalesce(json_agg(json_build_array(origen, n)), CAST('[]' AS json))
                              FROM (SELECT origen, count(*) AS n FROM base GROUP BY origen) x),
                'porPsicorientador', (SELECT coalesce(json_agg(json_build_array(nombre, atendidas, completadas)
                                                               ORDER BY atendidas DESC, nombre), CAST('[]' AS json))
                                      FROM (SELECT p.per_nombres || ' ' || p.per_apellidos AS nombre, count(*) AS atendidas,
                                                   count(*) FILTER (WHERE b.estado = 'COMPLETADA') AS completadas
                                            FROM base b JOIN personal p ON p.per_id = b.psi_id
                                            GROUP BY p.per_id, p.per_nombres, p.per_apellidos) x)
            ) AS text)
            """.formatted(DIA_ALERTA, RangoEdad.sqlEnFecha("e.est_fecha_nacimiento", DIA_ALERTA));

    // citas y valoraciones no guardan el grupo: se filtran con el grupo y la edad que tiene hoy el estudiante
    private static final String CONSULTA_CITAS_Y_FILTROS = """
            SELECT CAST(json_build_object(
                'citas', (SELECT json_build_array(count(*) FILTER (WHERE c.cit_estado = 'REALIZADA'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'NO_ASISTIO'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'CANCELADA'),
                                                  count(*) FILTER (WHERE c.cit_estado = 'PROGRAMADA'))
                          FROM citas c
                          JOIN estudiantes e ON e.est_id = c.cit_est_id
                          WHERE (CAST(:anio AS integer) IS NULL
                                 OR extract(year FROM c.cit_inicio AT TIME ZONE CAST(:zona AS text)) = CAST(:anio AS integer))
                            AND (CAST(:desde AS date) IS NULL
                                 OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                            AND (CAST(:hasta AS date) IS NULL
                                 OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                            AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
                            AND (CAST(:edad AS text) IS NULL OR (%1$s) = CAST(:edad AS text))
                            AND (CAST(:categoriaId AS bigint) IS NULL OR EXISTS (
                                  SELECT 1 FROM citas_alertas ca JOIN alertas a ON a.ale_id = ca.cia_ale_id
                                  WHERE ca.cia_cit_id = c.cit_id AND a.ale_cat_id = CAST(:categoriaId AS bigint)))),
                'valoraciones', (SELECT json_build_array(count(*), count(DISTINCT v.val_est_id))
                                 FROM valoraciones v
                                 JOIN estudiantes e ON e.est_id = v.val_est_id
                                 WHERE (CAST(:anio AS integer) IS NULL
                                        OR extract(year FROM v.val_creado_en AT TIME ZONE CAST(:zona AS text)) = CAST(:anio AS integer))
                                   AND (CAST(:desde AS date) IS NULL
                                        OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                                   AND (CAST(:hasta AS date) IS NULL
                                        OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                                   AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
                                   AND (CAST(:edad AS text) IS NULL OR (%1$s) = CAST(:edad AS text))),
                'anios', (SELECT coalesce(json_agg(anio ORDER BY anio DESC), CAST('[]' AS json))
                          FROM (SELECT DISTINCT CAST(extract(year FROM ale_creado_en AT TIME ZONE CAST(:zona AS text)) AS integer) AS anio
                                FROM alertas) x),
                'grupos', (SELECT coalesce(json_agg(grupo ORDER BY grupo), CAST('[]' AS json))
                           FROM (SELECT est_grupo AS grupo FROM estudiantes WHERE est_grupo IS NOT NULL
                                 UNION
                                 SELECT ale_grupo FROM alertas WHERE ale_grupo IS NOT NULL) x),
                'categorias', (SELECT coalesce(json_agg(json_build_array(cat_id, cat_nombre) ORDER BY cat_nombre), CAST('[]' AS json))
                               FROM categorias_alerta WHERE cat_activa)
            ) AS text)
            """.formatted(RangoEdad.SQL_HOY);

    private final EntityManager em;

    public EstadisticaRepository(EntityManager em) {
        this.em = em;
    }

    // json con todos los conteos de las alertas (ver CONSULTA_ALERTAS)
    public String alertas(FiltroEstadistica filtro, String zona) {
        return (String) consulta(CONSULTA_ALERTAS, filtro, zona).getSingleResult();
    }

    // json con citas, valoraciones y las listas de los filtros
    public String citasYFiltros(FiltroEstadistica filtro, String zona) {
        return (String) consulta(CONSULTA_CITAS_Y_FILTROS, filtro, zona).getSingleResult();
    }

    // ---------------------------------------------------------------- ayudas

    private Query consulta(String sql, FiltroEstadistica filtro, String zona) {

        Query query = em.createNativeQuery(sql);
        query.setParameter("anio", filtro.anio());
        query.setParameter("desde", fecha(filtro.desde()));
        query.setParameter("hasta", fecha(filtro.hasta()));
        query.setParameter("grupo", filtro.grupo());
        query.setParameter("edad", filtro.edad());
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
