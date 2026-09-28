-- codigo al azar para usar en las urls y la api en vez del id (el id no sale del backend).
-- el backend genera los nuevos; aqui solo se llenan las filas que ya existian

ALTER TABLE personal ADD COLUMN per_codigo varchar(16);
UPDATE personal SET per_codigo = substr(md5(random()::text || per_id || clock_timestamp()), 1, 12);
ALTER TABLE personal ALTER COLUMN per_codigo SET NOT NULL;
ALTER TABLE personal ADD CONSTRAINT uq_personal_codigo UNIQUE (per_ins_id, per_codigo);

-- el del qr va aparte para poder regenerarlo sin cambiar los enlaces del estudiante
ALTER TABLE estudiantes ADD COLUMN est_codigo varchar(16);
ALTER TABLE estudiantes ADD COLUMN est_codigo_qr varchar(16);
UPDATE estudiantes SET
    est_codigo = substr(md5(random()::text || est_id || clock_timestamp()), 1, 12),
    est_codigo_qr = substr(md5(random()::text || est_id || 'qr' || clock_timestamp()), 1, 12);
ALTER TABLE estudiantes ALTER COLUMN est_codigo SET NOT NULL;
ALTER TABLE estudiantes ALTER COLUMN est_codigo_qr SET NOT NULL;
ALTER TABLE estudiantes ADD CONSTRAINT uq_estudiantes_codigo UNIQUE (est_ins_id, est_codigo);
ALTER TABLE estudiantes ADD CONSTRAINT uq_estudiantes_codigo_qr UNIQUE (est_ins_id, est_codigo_qr);

ALTER TABLE alertas ADD COLUMN ale_codigo varchar(16);
UPDATE alertas SET ale_codigo = substr(md5(random()::text || ale_id || clock_timestamp()), 1, 12);
ALTER TABLE alertas ALTER COLUMN ale_codigo SET NOT NULL;
ALTER TABLE alertas ADD CONSTRAINT uq_alertas_codigo UNIQUE (ale_ins_id, ale_codigo);

ALTER TABLE citas ADD COLUMN cit_codigo varchar(16);
UPDATE citas SET cit_codigo = substr(md5(random()::text || cit_id || clock_timestamp()), 1, 12);
ALTER TABLE citas ALTER COLUMN cit_codigo SET NOT NULL;
ALTER TABLE citas ADD CONSTRAINT uq_citas_codigo UNIQUE (cit_ins_id, cit_codigo);
