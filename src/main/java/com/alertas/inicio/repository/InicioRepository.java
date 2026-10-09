package com.alertas.inicio.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

// el dashboard de cada rol sale de una o dos consultas que devuelven json (mas la del colegio para RLS).
// :usuario es el usuario de la sesion; de ahi se saca el personal cuando hace falta, sin otra consulta
@Repository
public class InicioRepository {

    private static final String ANIO_ACTIVO = "(SELECT anl_id FROM anios_lectivos WHERE anl_activo)";
    private static final String YO = "(SELECT per_id FROM personal WHERE per_usu_id = CAST(:usuario AS bigint))";

    // ---------------------------------------------------------------- admin

    private static final String ADMIN_CONTEOS = """
            SELECT CAST(json_build_object(
                'estudiantes', (SELECT count(*) FROM estudiantes e JOIN usuarios u ON u.usu_id = e.est_usu_id WHERE u.usu_activo),
                'docentes', (SELECT count(*) FROM usuarios WHERE usu_activo AND usu_rol = 'DOCENTE'),
                'psicorientadores', (SELECT count(*) FROM usuarios WHERE usu_activo AND usu_rol = 'PSICORIENTADOR'),
                'estados', (SELECT json_build_array(count(*) FILTER (WHERE ale_estado = 'PENDIENTE'),
                                                    count(*) FILTER (WHERE ale_estado = 'EN_PROCESO'),
                                                    count(*) FILTER (WHERE ale_estado = 'COMPLETADA'))
                            FROM alertas WHERE ale_anl_id = %1$s),
                'sinAtender', (SELECT json_build_array(count(DISTINCT ale_est_id),
                                                       count(DISTINCT ale_est_id) FILTER (WHERE ale_prioritaria))
                               FROM alertas WHERE ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND ale_psi_id IS NULL),
                'porMes', (SELECT coalesce(json_agg(json_build_array(mes, n) ORDER BY mes), CAST('[]' AS json))
                           FROM (SELECT to_char(ale_creado_en AT TIME ZONE CAST(:zona AS text), 'YYYY-MM') AS mes, count(*) AS n
                                 FROM alertas
                                 WHERE ale_creado_en >= date_trunc('month', now()) - interval '5 months'
                                 GROUP BY 1) x)
            ) AS text)
            """.formatted(ANIO_ACTIVO);

    // las ultimas alertas del colegio (el admin puede abrir el expediente)
    private static final String ULTIMAS_ALERTAS = """
            SELECT CAST(coalesce(json_agg(json_build_array(nombre, detalle, creado, estado, nivel, enlace, grupo, peligro) ORDER BY creado DESC),
                                 CAST('[]' AS json)) AS text)
            FROM (SELECT e.est_nombres || ' ' || e.est_apellidos AS nombre,
                         c.cat_nombre AS detalle, a.ale_creado_en AS creado, a.ale_estado AS estado, a.ale_nivel AS nivel,
                         %s AS enlace,
                         gr.gra_nombre || ' ' || g.grp_nombre AS grupo, a.ale_peligro_inmediato AS peligro
                  FROM alertas a
                  JOIN estudiantes e ON e.est_id = a.ale_est_id
                  JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
                  JOIN grupos g ON g.grp_id = a.ale_grp_id
                  JOIN grados gr ON gr.gra_id = g.grp_gra_id
                  WHERE %s
                  ORDER BY a.ale_creado_en DESC
                  LIMIT 6) x
            """;

    // ---------------------------------------------------------------- psicorientador

    private static final String PSICORIENTADOR_CONTEOS = """
            SELECT CAST(json_build_object(
                'bandeja', (SELECT json_build_array(count(DISTINCT ale_est_id),
                                                    count(DISTINCT ale_est_id) FILTER (WHERE ale_prioritaria))
                            FROM alertas WHERE ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND ale_psi_id IS NULL),
                'misCasos', (SELECT count(DISTINCT ale_est_id) FROM alertas
                             WHERE ale_psi_id = %1$s AND ale_estado IN ('PENDIENTE', 'EN_PROCESO')),
                'citasHoy', (SELECT count(*) FROM citas
                             WHERE cit_psi_id = %1$s AND cit_estado = 'PROGRAMADA'
                               AND CAST(cit_inicio AT TIME ZONE CAST(:zona AS text) AS date)
                                   = CAST(now() AT TIME ZONE CAST(:zona AS text) AS date)),
                'estados', (SELECT json_build_array(count(*) FILTER (WHERE ale_estado = 'PENDIENTE'),
                                                    count(*) FILTER (WHERE ale_estado = 'EN_PROCESO'),
                                                    count(*) FILTER (WHERE ale_estado = 'COMPLETADA'))
                            FROM alertas WHERE ale_psi_id = %1$s),
                'porValorar', (SELECT CASE WHEN NOT i.ins_valoraciones_activas THEN NULL ELSE (
                                   SELECT count(*)
                                   FROM matriculas m
                                   JOIN estudiantes e ON e.est_id = m.mat_est_id
                                   JOIN usuarios u ON u.usu_id = e.est_usu_id
                                   WHERE m.mat_anl_id = %2$s AND m.mat_estado <> 'RETIRADA' AND u.usu_activo
                                     AND NOT EXISTS (SELECT 1 FROM valoraciones v
                                                     WHERE v.val_est_id = e.est_id
                                                       AND v.val_creado_en >= now() - make_interval(days => i.ins_valoraciones_dias)))
                               END
                               FROM instituciones i WHERE i.ins_id = tenant_actual())
            ) AS text)
            """.formatted(YO, ANIO_ACTIVO);

    private static final String PROXIMAS_CITAS = """
            SELECT CAST(coalesce(json_agg(json_build_array(nombre, detalle, inicio, estado, enlace) ORDER BY inicio), CAST('[]' AS json)) AS text)
            FROM (SELECT e.est_nombres || ' ' || e.est_apellidos AS nombre,
                         CASE WHEN c.cit_modalidad = 'VIRTUAL' THEN 'Virtual' ELSE coalesce(c.cit_lugar, 'Presencial') END AS detalle,
                         c.cit_inicio AS inicio,
                         CASE WHEN c.cit_iniciada_en IS NULL THEN 'PROGRAMADA' ELSE 'EN_CURSO' END AS estado,
                         '/atencion/citas/' || c.cit_codigo AS enlace
                  FROM citas c
                  JOIN estudiantes e ON e.est_id = c.cit_est_id
                  WHERE c.cit_psi_id = %s AND c.cit_estado = 'PROGRAMADA'
                  ORDER BY c.cit_inicio
                  LIMIT 6) x
            """.formatted(YO);

    // ---------------------------------------------------------------- docente

    private static final String DOCENTE_CONTEOS = """
            SELECT CAST(json_build_object(
                'estados', (SELECT json_build_array(count(*) FILTER (WHERE ale_estado = 'PENDIENTE'),
                                                    count(*) FILTER (WHERE ale_estado = 'EN_PROCESO'),
                                                    count(*) FILTER (WHERE ale_estado = 'COMPLETADA'))
                            FROM alertas WHERE ale_reportada_por = CAST(:usuario AS bigint)),
                'porNivel', (SELECT coalesce(json_agg(json_build_array(ale_nivel, n)), CAST('[]' AS json))
                             FROM (SELECT ale_nivel, count(*) AS n FROM alertas
                                   WHERE ale_reportada_por = CAST(:usuario AS bigint) GROUP BY ale_nivel) x)
            ) AS text)
            """;

    // ---------------------------------------------------------------- estudiante

    // el estudiante solo ve estados y citas: nunca quien lo reporto, la categoria, el nivel ni las observaciones
    private static final String ESTUDIANTE = """
            WITH yo AS (SELECT est_id FROM estudiantes WHERE est_usu_id = CAST(:usuario AS bigint))
            SELECT CAST(json_build_object(
                'cita', (SELECT json_build_array(c.cit_inicio, c.cit_modalidad, c.cit_lugar)
                         FROM citas c
                         WHERE c.cit_est_id = (SELECT est_id FROM yo) AND c.cit_estado = 'PROGRAMADA'
                         ORDER BY c.cit_inicio LIMIT 1),
                'solicitudes', (SELECT json_build_array(count(*), count(*) FILTER (WHERE a.ale_estado <> 'COMPLETADA'))
                                FROM alertas a
                                WHERE a.ale_reportada_por = CAST(:usuario AS bigint) AND a.ale_origen = 'ESTUDIANTE'),
                'orientador', (SELECT p.per_nombres || ' ' || p.per_apellidos
                               FROM alertas a JOIN personal p ON p.per_id = a.ale_psi_id
                               WHERE a.ale_est_id = (SELECT est_id FROM yo) AND a.ale_estado IN ('PENDIENTE', 'EN_PROCESO')
                               ORDER BY a.ale_asignada_en DESC NULLS LAST
                               LIMIT 1),
                'estados', (SELECT json_build_array(count(*) FILTER (WHERE ale_estado = 'PENDIENTE'),
                                                    count(*) FILTER (WHERE ale_estado = 'EN_PROCESO'),
                                                    count(*) FILTER (WHERE ale_estado = 'COMPLETADA'))
                            FROM alertas WHERE ale_est_id = (SELECT est_id FROM yo)),
                'citas', (SELECT coalesce(json_agg(json_build_array(titulo, detalle, inicio, estado, enlace) ORDER BY orden, clave),
                                          CAST('[]' AS json))
                          FROM (SELECT 'Cita con ' || p.per_nombres || ' ' || p.per_apellidos AS titulo,
                                       CASE WHEN c.cit_modalidad = 'VIRTUAL' THEN 'Virtual' ELSE coalesce(c.cit_lugar, 'Presencial') END AS detalle,
                                       c.cit_inicio AS inicio,
                                       CASE WHEN c.cit_estado = 'PROGRAMADA' AND c.cit_iniciada_en IS NOT NULL THEN 'EN_CURSO'
                                            ELSE c.cit_estado END AS estado,
                                       '/mi-proceso' AS enlace,
                                       CASE WHEN c.cit_estado = 'PROGRAMADA' THEN 0 ELSE 1 END AS orden,
                                       CASE WHEN c.cit_estado = 'PROGRAMADA' THEN extract(epoch FROM c.cit_inicio)
                                            ELSE -extract(epoch FROM c.cit_inicio) END AS clave
                                FROM citas c
                                JOIN personal p ON p.per_id = c.cit_psi_id
                                WHERE c.cit_est_id = (SELECT est_id FROM yo)
                                ORDER BY orden, clave
                                LIMIT 6) x)
            ) AS text)
            """;

    private final EntityManager em;

    public InicioRepository(EntityManager em) {
        this.em = em;
    }

    public String conteosAdmin(String zona) {
        return (String) em.createNativeQuery(ADMIN_CONTEOS).setParameter("zona", zona).getSingleResult();
    }

    public String ultimasAlertasDelColegio() {
        String sql = ULTIMAS_ALERTAS.formatted("'/atencion/estudiantes/' || e.est_codigo", "true");
        return (String) em.createNativeQuery(sql).getSingleResult();
    }

    public String conteosPsicorientador(Long usuarioId, String zona) {
        return (String) em.createNativeQuery(PSICORIENTADOR_CONTEOS)
                .setParameter("usuario", usuarioId)
                .setParameter("zona", zona)
                .getSingleResult();
    }

    public String proximasCitas(Long usuarioId) {
        return (String) em.createNativeQuery(PROXIMAS_CITAS).setParameter("usuario", usuarioId).getSingleResult();
    }

    public String conteosDocente(Long usuarioId) {
        return (String) em.createNativeQuery(DOCENTE_CONTEOS).setParameter("usuario", usuarioId).getSingleResult();
    }

    public String ultimasAlertasDe(Long usuarioId) {
        String sql = ULTIMAS_ALERTAS.formatted("'/alertas/' || a.ale_codigo", "a.ale_reportada_por = CAST(:usuario AS bigint)");
        return (String) em.createNativeQuery(sql).setParameter("usuario", usuarioId).getSingleResult();
    }

    public String estudiante(Long usuarioId) {
        return (String) em.createNativeQuery(ESTUDIANTE).setParameter("usuario", usuarioId).getSingleResult();
    }
}
