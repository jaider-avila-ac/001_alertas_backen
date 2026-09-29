-- estadisticas del superadmin: cruzan colegios. el superadmin no tiene tenant, asi que RLS no le deja ver filas.
-- estas funciones corren como el duenio de las tablas (sin FORCE en RLS, ver V7) y devuelven SOLO totales:
-- nunca nombres de estudiantes ni el contenido de las alertas.
-- p_ins null = todos los colegios. p_desde / p_hasta null = sin limite. p_zona: zona para saber el dia y el mes

-- totales de arriba
CREATE FUNCTION sa_resumen(p_ins bigint, p_desde date, p_hasta date, p_zona text)
RETURNS TABLE (instituciones_activas bigint, instituciones_inactivas bigint, alertas bigint,
               sms_enviados bigint, sms_fallidos bigint, sms_segmentos bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT
        (SELECT count(*) FROM instituciones i WHERE i.ins_activa AND (p_ins IS NULL OR i.ins_id = p_ins)),
        (SELECT count(*) FROM instituciones i WHERE NOT i.ins_activa AND (p_ins IS NULL OR i.ins_id = p_ins)),
        (SELECT count(*) FROM alertas a
          WHERE (p_ins IS NULL OR a.ale_ins_id = p_ins)
            AND (p_desde IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date >= p_desde)
            AND (p_hasta IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date <= p_hasta)),
        (SELECT count(*) FROM sms_envios s
          WHERE s.sms_estado = 'ENVIADO' AND (p_ins IS NULL OR s.sms_ins_id = p_ins)
            AND (p_desde IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date >= p_desde)
            AND (p_hasta IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date <= p_hasta)),
        (SELECT count(*) FROM sms_envios s
          WHERE s.sms_estado = 'FALLIDO' AND (p_ins IS NULL OR s.sms_ins_id = p_ins)
            AND (p_desde IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date >= p_desde)
            AND (p_hasta IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date <= p_hasta)),
        (SELECT coalesce(sum(s.sms_segmentos), 0) FROM sms_envios s
          WHERE s.sms_estado = 'ENVIADO' AND (p_ins IS NULL OR s.sms_ins_id = p_ins)
            AND (p_desde IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date >= p_desde)
            AND (p_hasta IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date <= p_hasta))
$$;

-- usuarios activos por rol, de colegios activos
CREATE FUNCTION sa_usuarios_por_rol(p_ins bigint)
RETURNS TABLE (rol text, total bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT u.usu_rol::text, count(*)
    FROM usuarios u
    JOIN instituciones i ON i.ins_id = u.usu_ins_id
    WHERE u.usu_activo AND i.ins_activa AND (p_ins IS NULL OR u.usu_ins_id = p_ins)
    GROUP BY u.usu_rol
$$;

-- alertas por mes (yyyy-mm)
CREATE FUNCTION sa_alertas_por_mes(p_ins bigint, p_desde date, p_hasta date, p_zona text)
RETURNS TABLE (mes text, total bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT to_char(a.ale_creado_en AT TIME ZONE p_zona, 'YYYY-MM'), count(*)
    FROM alertas a
    WHERE (p_ins IS NULL OR a.ale_ins_id = p_ins)
      AND (p_desde IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date >= p_desde)
      AND (p_hasta IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
    GROUP BY 1
    ORDER BY 1
$$;

-- alertas por categoria. cada colegio tiene sus categorias: se juntan las que se llaman igual
-- (sin importar mayusculas ni tildes)
CREATE FUNCTION sa_alertas_por_categoria(p_ins bigint, p_desde date, p_hasta date, p_zona text)
RETURNS TABLE (categoria text, total bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT min(c.cat_nombre), count(*)
    FROM alertas a
    JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
    WHERE (p_ins IS NULL OR a.ale_ins_id = p_ins)
      AND (p_desde IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date >= p_desde)
      AND (p_hasta IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
    GROUP BY lower(f_unaccent(c.cat_nombre))
    ORDER BY count(*) DESC, 1
$$;

-- sms por mes: enviados, fallidos y segmentos (lo que se cobra)
CREATE FUNCTION sa_sms_por_mes(p_ins bigint, p_desde date, p_hasta date, p_zona text)
RETURNS TABLE (mes text, enviados bigint, fallidos bigint, segmentos bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT to_char(s.sms_creado_en AT TIME ZONE p_zona, 'YYYY-MM'),
           count(*) FILTER (WHERE s.sms_estado = 'ENVIADO'),
           count(*) FILTER (WHERE s.sms_estado = 'FALLIDO'),
           coalesce(sum(s.sms_segmentos) FILTER (WHERE s.sms_estado = 'ENVIADO'), 0)
    FROM sms_envios s
    WHERE (p_ins IS NULL OR s.sms_ins_id = p_ins)
      AND (p_desde IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date >= p_desde)
      AND (p_hasta IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
    GROUP BY 1
    ORDER BY 1
$$;

-- comparativo entre colegios, paginado, de los que mas alertas tienen a los que menos
CREATE FUNCTION sa_comparativo(p_desde date, p_hasta date, p_zona text, p_limite integer, p_saltar integer)
RETURNS TABLE (nombre text, slug text, activa boolean, estudiantes bigint, alertas bigint, pendientes bigint,
               en_proceso bigint, completadas bigint, sms_enviados bigint, sms_segmentos bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    WITH al AS (
        SELECT a.ale_ins_id AS ins_id, count(*) AS total,
               count(*) FILTER (WHERE a.ale_estado = 'PENDIENTE') AS pendientes,
               count(*) FILTER (WHERE a.ale_estado = 'EN_PROCESO') AS en_proceso,
               count(*) FILTER (WHERE a.ale_estado = 'COMPLETADA') AS completadas
        FROM alertas a
        WHERE (p_desde IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date >= p_desde)
          AND (p_hasta IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
        GROUP BY a.ale_ins_id
    ), sm AS (
        SELECT s.sms_ins_id AS ins_id, count(*) AS enviados, sum(s.sms_segmentos) AS segmentos
        FROM sms_envios s
        WHERE s.sms_estado = 'ENVIADO'
          AND (p_desde IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date >= p_desde)
          AND (p_hasta IS NULL OR (s.sms_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
        GROUP BY s.sms_ins_id
    ), es AS (
        SELECT u.usu_ins_id AS ins_id, count(*) AS total
        FROM usuarios u
        WHERE u.usu_activo AND u.usu_rol = 'ESTUDIANTE'
        GROUP BY u.usu_ins_id
    )
    SELECT i.ins_nombre::text, i.ins_slug::text, i.ins_activa,
           coalesce(es.total, 0), coalesce(al.total, 0), coalesce(al.pendientes, 0),
           coalesce(al.en_proceso, 0), coalesce(al.completadas, 0),
           coalesce(sm.enviados, 0), coalesce(sm.segmentos, 0)
    FROM instituciones i
    LEFT JOIN al ON al.ins_id = i.ins_id
    LEFT JOIN sm ON sm.ins_id = i.ins_id
    LEFT JOIN es ON es.ins_id = i.ins_id
    ORDER BY coalesce(al.total, 0) DESC, i.ins_nombre
    LIMIT p_limite OFFSET p_saltar
$$;

-- solo el backend las llama
REVOKE ALL ON FUNCTION sa_resumen(bigint, date, date, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION sa_usuarios_por_rol(bigint) FROM PUBLIC;
REVOKE ALL ON FUNCTION sa_alertas_por_mes(bigint, date, date, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION sa_alertas_por_categoria(bigint, date, date, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION sa_sms_por_mes(bigint, date, date, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION sa_comparativo(date, date, text, integer, integer) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION sa_resumen(bigint, date, date, text) TO alertas_app;
GRANT EXECUTE ON FUNCTION sa_usuarios_por_rol(bigint) TO alertas_app;
GRANT EXECUTE ON FUNCTION sa_alertas_por_mes(bigint, date, date, text) TO alertas_app;
GRANT EXECUTE ON FUNCTION sa_alertas_por_categoria(bigint, date, date, text) TO alertas_app;
GRANT EXECUTE ON FUNCTION sa_sms_por_mes(bigint, date, date, text) TO alertas_app;
GRANT EXECUTE ON FUNCTION sa_comparativo(date, date, text, integer, integer) TO alertas_app;
