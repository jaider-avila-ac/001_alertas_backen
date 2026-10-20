-- nombres mas cortos para las 10 categorias: se leen mejor en las listas y en los graficos.
-- solo cambia el texto; las alertas siguen apuntando a la misma categoria
UPDATE categorias_alerta c
SET cat_nombre = v.corto
FROM (VALUES
    ('Salud mental y emocional', 'Salud mental'),
    ('Convivencia escolar', 'Convivencia escolar'),
    ('Violencia y vulneracion de derechos', 'Vulneración de derechos'),
    ('Asistencia y permanencia escolar', 'Asistencia escolar'),
    ('Consumo de sustancias psicoactivas', 'Consumo de sustancias'),
    ('Dificultades academicas y de aprendizaje', 'Dificultades académicas'),
    ('Salud y bienestar fisico', 'Salud física'),
    ('Riesgos de seguridad escolar', 'Seguridad escolar'),
    ('Proteccion e inclusion', 'Protección e inclusión'),
    ('Situaciones familiares y sociales', 'Situación familiar')
) AS v(largo, corto)
WHERE lower(f_unaccent(c.cat_nombre)) = lower(f_unaccent(v.largo));

-- en una base nueva las crea con el nombre corto (la V21 las crea con el largo y aqui se renombran)
DO $$
BEGIN
    IF (SELECT count(*) FROM categorias_alerta) <> 10 THEN
        RAISE EXCEPTION 'se esperaban 10 categorias';
    END IF;
END $$;
