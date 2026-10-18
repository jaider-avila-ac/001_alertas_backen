-- las categorias pasan a ser fijas: las mismas 10 en todas las instituciones y siempre activas

-- las viejas que tienen equivalente se renombran, asi sus alertas quedan en la nueva
UPDATE categorias_alerta c SET cat_nombre = v.nueva
FROM (VALUES
    ('salud mental', 'Salud mental y emocional'),
    ('convivencia y acoso escolar', 'Convivencia escolar'),
    ('violencia intrafamiliar', 'Violencia y vulneracion de derechos'),
    ('ausentismo escolar', 'Asistencia y permanencia escolar'),
    ('consumo de sustancias', 'Consumo de sustancias psicoactivas')
) AS v(vieja, nueva)
WHERE lower(f_unaccent(c.cat_nombre)) = v.vieja
  AND NOT EXISTS (
      SELECT 1 FROM categorias_alerta o
      WHERE o.cat_ins_id = c.cat_ins_id AND lower(f_unaccent(o.cat_nombre)) = lower(f_unaccent(v.nueva)));

-- las que falten en cada institucion
INSERT INTO categorias_alerta (cat_ins_id, cat_nombre, cat_activa)
SELECT i.ins_id, v.nombre, true
FROM instituciones i
CROSS JOIN (VALUES
    ('Salud mental y emocional'),
    ('Convivencia escolar'),
    ('Violencia y vulneracion de derechos'),
    ('Asistencia y permanencia escolar'),
    ('Consumo de sustancias psicoactivas'),
    ('Dificultades academicas y de aprendizaje'),
    ('Salud y bienestar fisico'),
    ('Riesgos de seguridad escolar'),
    ('Proteccion e inclusion'),
    ('Situaciones familiares y sociales')
) AS v(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM categorias_alerta c
    WHERE c.cat_ins_id = i.ins_id AND lower(f_unaccent(c.cat_nombre)) = lower(f_unaccent(v.nombre)));

-- las que no estan en la lista: sin alertas se borran, con alertas quedan apagadas como historial
DELETE FROM categorias_alerta c
WHERE lower(f_unaccent(c.cat_nombre)) NOT IN (
        'salud mental y emocional', 'convivencia escolar', 'violencia y vulneracion de derechos',
        'asistencia y permanencia escolar', 'consumo de sustancias psicoactivas',
        'dificultades academicas y de aprendizaje', 'salud y bienestar fisico', 'riesgos de seguridad escolar',
        'proteccion e inclusion', 'situaciones familiares y sociales')
  AND NOT EXISTS (SELECT 1 FROM alertas a WHERE a.ale_cat_id = c.cat_id);

UPDATE categorias_alerta c SET cat_activa = (lower(f_unaccent(c.cat_nombre)) IN (
        'salud mental y emocional', 'convivencia escolar', 'violencia y vulneracion de derechos',
        'asistencia y permanencia escolar', 'consumo de sustancias psicoactivas',
        'dificultades academicas y de aprendizaje', 'salud y bienestar fisico', 'riesgos de seguridad escolar',
        'proteccion e inclusion', 'situaciones familiares y sociales'));

-- la app solo las crea con la institucion: no las cambia ni las borra
REVOKE UPDATE, DELETE ON categorias_alerta FROM alertas_app;
