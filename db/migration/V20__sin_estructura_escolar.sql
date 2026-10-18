-- la plataforma ya no organiza por anios, grados, grupos ni matriculas: sirve igual para colegios,
-- universidades e instituciones tecnicas. el estudiante tiene un grupo de texto libre y opcional
-- ("6A", "Ing. Sistemas - Semestre 3"), y los registros se ubican por su fecha

ALTER TABLE estudiantes ADD COLUMN est_grupo varchar(60);
-- el de la alerta es el que tenia el estudiante ese dia: el historial no cambia si despues cambia de grupo
ALTER TABLE alertas ADD COLUMN ale_grupo varchar(60);

-- lo que ya habia pasa a texto: "Sexto A"
UPDATE estudiantes e SET est_grupo = g.gra_nombre || ' ' || gr.grp_nombre
FROM matriculas m
JOIN grupos gr ON gr.grp_id = m.mat_grp_id
JOIN grados g ON g.gra_id = gr.grp_gra_id
WHERE m.mat_est_id = e.est_id AND m.mat_estado = 'ACTIVA';

UPDATE alertas a SET ale_grupo = g.gra_nombre || ' ' || gr.grp_nombre
FROM grupos gr
JOIN grados g ON g.gra_id = gr.grp_gra_id
WHERE gr.grp_id = a.ale_grp_id;

-- al quitar las columnas se van tambien sus llaves e indices
ALTER TABLE alertas
    DROP COLUMN ale_anl_id,
    DROP COLUMN ale_grp_id,
    DROP COLUMN ale_mat_id;

ALTER TABLE citas
    DROP COLUMN cit_grp_id,
    DROP COLUMN cit_mat_id;

ALTER TABLE valoraciones
    DROP COLUMN val_mat_id;

DROP TABLE matricula_movimientos;
DROP TABLE matriculas;
DROP TABLE grupos;
DROP TABLE grados;
DROP TABLE anios_lectivos;

-- para filtrar por grupo sin importar mayusculas
CREATE INDEX ix_estudiantes_grupo ON estudiantes (est_ins_id, lower(est_grupo));
