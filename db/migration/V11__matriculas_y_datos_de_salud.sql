-- ubicaciones pasa a ser matriculas: una por estudiante por anio, con estado, origen y fechas.
-- los cambios de grupo o de grado dentro del anio quedan en matricula_movimientos, nada se sobrescribe.
-- alertas y citas guardan la matricula del momento.
-- ademas: datos de salud y contacto del estudiante

-- ---------------------------------------------------------------- matriculas

ALTER TABLE ubicaciones RENAME TO matriculas;

ALTER TABLE matriculas RENAME COLUMN ubi_id             TO mat_id;
ALTER TABLE matriculas RENAME COLUMN ubi_ins_id         TO mat_ins_id;
ALTER TABLE matriculas RENAME COLUMN ubi_est_id         TO mat_est_id;
ALTER TABLE matriculas RENAME COLUMN ubi_anl_id         TO mat_anl_id;
ALTER TABLE matriculas RENAME COLUMN ubi_grp_id         TO mat_grp_id;
ALTER TABLE matriculas RENAME COLUMN ubi_creado_en      TO mat_creado_en;
ALTER TABLE matriculas RENAME COLUMN ubi_actualizado_en TO mat_actualizado_en;

ALTER TABLE matriculas RENAME CONSTRAINT uq_ubicaciones_tenant_id   TO uq_matriculas_tenant_id;
ALTER TABLE matriculas RENAME CONSTRAINT uq_ubicaciones_est_anio    TO uq_matriculas_est_anio;
ALTER TABLE matriculas RENAME CONSTRAINT fk_ubicaciones_estudiante  TO fk_matriculas_estudiante;
ALTER TABLE matriculas RENAME CONSTRAINT fk_ubicaciones_grupo       TO fk_matriculas_grupo;
ALTER INDEX ix_ubicaciones_grupo RENAME TO ix_matriculas_grupo;
ALTER INDEX ix_ubicaciones_anio  RENAME TO ix_matriculas_anio;
ALTER INDEX ubicaciones_pkey     RENAME TO matriculas_pkey;
ALTER POLICY p_ubicaciones_tenant ON matriculas RENAME TO p_matriculas_tenant;

-- ACTIVA: en curso. las demas son el resultado del anio
ALTER TABLE matriculas ADD COLUMN mat_estado varchar(10) NOT NULL DEFAULT 'ACTIVA';
-- NUEVA: primera vez en el colegio. PROMOCION: paso de grado. REPITE: mismo grado que el anio anterior
ALTER TABLE matriculas ADD COLUMN mat_origen varchar(10) NOT NULL DEFAULT 'NUEVA';
ALTER TABLE matriculas ADD COLUMN mat_fecha_matricula date NOT NULL DEFAULT current_date;
ALTER TABLE matriculas ADD COLUMN mat_fecha_cierre date;
ALTER TABLE matriculas ADD COLUMN mat_motivo_cierre varchar(300);

-- las que ya existian: la fecha es la de creacion. las de anios que ya pasaron quedan promovidas
UPDATE matriculas SET mat_fecha_matricula = mat_creado_en::date;
UPDATE matriculas m SET mat_estado = 'PROMOVIDA', mat_fecha_cierre = m.mat_actualizado_en::date
FROM anios_lectivos a, anios_lectivos activo
WHERE a.anl_id = m.mat_anl_id
  AND activo.anl_ins_id = a.anl_ins_id AND activo.anl_activo
  AND a.anl_anio < activo.anl_anio;

ALTER TABLE matriculas ADD CONSTRAINT ck_matriculas_estado
    CHECK (mat_estado IN ('ACTIVA', 'PROMOVIDA', 'REPROBADA', 'GRADUADA', 'RETIRADA'));
ALTER TABLE matriculas ADD CONSTRAINT ck_matriculas_origen
    CHECK (mat_origen IN ('NUEVA', 'PROMOCION', 'REPITE'));
ALTER TABLE matriculas ADD CONSTRAINT ck_matriculas_cierre
    CHECK ((mat_estado = 'ACTIVA') = (mat_fecha_cierre IS NULL));

-- para la fk de alertas y citas (matricula del mismo estudiante)
ALTER TABLE matriculas ADD CONSTRAINT uq_matriculas_tenant_est_id UNIQUE (mat_ins_id, mat_est_id, mat_id);

CREATE INDEX ix_matriculas_estudiante ON matriculas (mat_ins_id, mat_est_id);

-- nada de matriculas se borra desde la app: se cierran
REVOKE DELETE ON matriculas FROM alertas_app;


-- ---------------------------------------------------------------- movimientos

-- cada cambio de grupo o de grado dentro del mismo anio
CREATE TABLE matricula_movimientos (
    mov_id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mov_ins_id            bigint       NOT NULL,
    mov_mat_id            bigint       NOT NULL,
    mov_anl_id            bigint       NOT NULL,
    mov_grp_anterior_id   bigint       NOT NULL,
    mov_grp_nuevo_id      bigint       NOT NULL,
    mov_motivo            varchar(300),
    mov_usu_id            bigint       NOT NULL,  -- quien lo hizo
    mov_creado_en         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_matricula_movimientos_tenant_id UNIQUE (mov_ins_id, mov_id),
    CONSTRAINT fk_movimientos_matricula FOREIGN KEY (mov_ins_id, mov_mat_id) REFERENCES matriculas (mat_ins_id, mat_id),
    CONSTRAINT fk_movimientos_grupo_anterior FOREIGN KEY (mov_ins_id, mov_anl_id, mov_grp_anterior_id)
        REFERENCES grupos (grp_ins_id, grp_anl_id, grp_id),
    CONSTRAINT fk_movimientos_grupo_nuevo FOREIGN KEY (mov_ins_id, mov_anl_id, mov_grp_nuevo_id)
        REFERENCES grupos (grp_ins_id, grp_anl_id, grp_id),
    CONSTRAINT fk_movimientos_usuario FOREIGN KEY (mov_ins_id, mov_usu_id) REFERENCES usuarios (usu_ins_id, usu_id)
);

CREATE INDEX ix_matricula_movimientos_matricula ON matricula_movimientos (mov_ins_id, mov_mat_id, mov_creado_en);

ALTER TABLE matricula_movimientos ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_matricula_movimientos_tenant ON matricula_movimientos
    USING (mov_ins_id = tenant_actual()) WITH CHECK (mov_ins_id = tenant_actual());

-- historial: solo se agrega
GRANT SELECT, INSERT ON matricula_movimientos TO alertas_app;


-- ---------------------------------------------------------------- alertas y citas

-- todavia no hay alertas ni citas en ninguna base (llegan en las fases 9 y 10),
-- por eso las columnas nuevas pueden ser obligatorias desde ya
ALTER TABLE alertas ADD COLUMN ale_mat_id bigint NOT NULL;
ALTER TABLE alertas ALTER COLUMN ale_grp_id SET NOT NULL;
ALTER TABLE alertas ADD CONSTRAINT fk_alertas_matricula FOREIGN KEY (ale_ins_id, ale_est_id, ale_mat_id)
    REFERENCES matriculas (mat_ins_id, mat_est_id, mat_id);

ALTER TABLE citas ADD COLUMN cit_mat_id bigint NOT NULL;
ALTER TABLE citas ADD COLUMN cit_grp_id bigint NOT NULL;
ALTER TABLE citas ADD CONSTRAINT fk_citas_matricula FOREIGN KEY (cit_ins_id, cit_est_id, cit_mat_id)
    REFERENCES matriculas (mat_ins_id, mat_est_id, mat_id);
ALTER TABLE citas ADD CONSTRAINT fk_citas_grupo FOREIGN KEY (cit_ins_id, cit_grp_id)
    REFERENCES grupos (grp_ins_id, grp_id);


-- ---------------------------------------------------------------- datos del estudiante

-- salud: para emergencias. solo los ven el admin y el psicorientador
ALTER TABLE estudiantes ADD COLUMN est_eps              varchar(80);
ALTER TABLE estudiantes ADD COLUMN est_rh               varchar(3);
ALTER TABLE estudiantes ADD COLUMN est_condiciones_salud varchar(500);
-- contacto
ALTER TABLE estudiantes ADD COLUMN est_direccion        varchar(150);
ALTER TABLE estudiantes ADD COLUMN est_barrio           varchar(80);
ALTER TABLE estudiantes ADD COLUMN est_correo           varchar(120);

ALTER TABLE estudiantes ADD CONSTRAINT ck_estudiantes_rh
    CHECK (est_rh IN ('O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'));
