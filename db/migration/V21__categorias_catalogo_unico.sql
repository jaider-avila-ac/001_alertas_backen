-- las 10 categorias pasan a ser un solo catalogo para todas las instituciones.
-- antes cada institucion tenia su copia de las mismas 10; ahora la tabla no tiene institucion

-- 1. las alertas de categorias viejas que ya no se usan se borran (no hay datos reales)
DELETE FROM sms_envios s
USING alertas a, categorias_alerta c
WHERE s.sms_ale_id = a.ale_id AND c.cat_id = a.ale_cat_id AND NOT c.cat_activa;

DELETE FROM citas_alertas ca
USING alertas a, categorias_alerta c
WHERE ca.cia_ale_id = a.ale_id AND c.cat_id = a.ale_cat_id AND NOT c.cat_activa;

DELETE FROM alertas a
USING categorias_alerta c
WHERE c.cat_id = a.ale_cat_id AND NOT c.cat_activa;

DELETE FROM categorias_alerta WHERE NOT cat_activa;

-- 2. una sola fila por nombre: las alertas pasan a la primera de cada nombre
ALTER TABLE alertas DROP CONSTRAINT fk_alertas_categoria;

CREATE TEMP TABLE categoria_unica ON COMMIT DROP AS
SELECT lower(f_unaccent(cat_nombre)) AS clave, min(cat_id) AS cat_id
FROM categorias_alerta
GROUP BY 1;

UPDATE alertas a
SET ale_cat_id = u.cat_id
FROM categorias_alerta c
JOIN categoria_unica u ON u.clave = lower(f_unaccent(c.cat_nombre))
WHERE c.cat_id = a.ale_cat_id AND a.ale_cat_id <> u.cat_id;

DELETE FROM categorias_alerta c
WHERE NOT EXISTS (SELECT 1 FROM categoria_unica u WHERE u.cat_id = c.cat_id);

-- 3. la tabla ya no es de una institucion: sin rls, sin cat_ins_id y sin cat_activa (siempre se usan)
DROP POLICY p_categorias_alerta_tenant ON categorias_alerta;
ALTER TABLE categorias_alerta DISABLE ROW LEVEL SECURITY;
DROP INDEX uq_categorias_alerta_nombre;
ALTER TABLE categorias_alerta DROP CONSTRAINT uq_categorias_alerta_tenant_id;
ALTER TABLE categorias_alerta DROP COLUMN cat_ins_id;
ALTER TABLE categorias_alerta DROP COLUMN cat_activa;
CREATE UNIQUE INDEX uq_categorias_alerta_nombre ON categorias_alerta (lower(f_unaccent(cat_nombre)));

-- 4. el catalogo completo (en una base nueva todavia no hay ninguna)
INSERT INTO categorias_alerta (cat_nombre)
SELECT v.nombre
FROM (VALUES
    (1, 'Salud mental y emocional'),
    (2, 'Convivencia escolar'),
    (3, 'Violencia y vulneracion de derechos'),
    (4, 'Asistencia y permanencia escolar'),
    (5, 'Consumo de sustancias psicoactivas'),
    (6, 'Dificultades academicas y de aprendizaje'),
    (7, 'Salud y bienestar fisico'),
    (8, 'Riesgos de seguridad escolar'),
    (9, 'Proteccion e inclusion'),
    (10, 'Situaciones familiares y sociales')
) AS v(orden, nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM categorias_alerta c WHERE lower(f_unaccent(c.cat_nombre)) = lower(f_unaccent(v.nombre)))
ORDER BY v.orden;

ALTER TABLE alertas ADD CONSTRAINT fk_alertas_categoria FOREIGN KEY (ale_cat_id) REFERENCES categorias_alerta (cat_id);

-- 5. la app solo las lee
REVOKE INSERT, UPDATE, DELETE ON categorias_alerta FROM alertas_app;
GRANT SELECT ON categorias_alerta TO alertas_app;

-- 6. estadisticas generales: ahora la misma categoria es la misma fila en todos los colegios
CREATE OR REPLACE FUNCTION sa_alertas_por_categoria(p_ins bigint, p_desde date, p_hasta date, p_zona text)
RETURNS TABLE (categoria text, total bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT c.cat_nombre, count(*)
    FROM alertas a
    JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
    WHERE (p_ins IS NULL OR a.ale_ins_id = p_ins)
      AND (p_desde IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date >= p_desde)
      AND (p_hasta IS NULL OR (a.ale_creado_en AT TIME ZONE p_zona)::date <= p_hasta)
    GROUP BY c.cat_id, c.cat_nombre
    ORDER BY count(*) DESC, 1
$$;
