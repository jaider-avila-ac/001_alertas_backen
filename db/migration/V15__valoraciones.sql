-- valoraciones de rutina: el psicorientador pasa a ver a un estudiante aunque no tenga alertas.
-- no es alerta ni cita. si ve algo, crea una alerta aparte (sin enlace a la valoracion)

-- el admin del colegio la enciende y dice cada cuantos dias le toca a cada estudiante
ALTER TABLE instituciones ADD COLUMN ins_valoraciones_activas boolean NOT NULL DEFAULT false;
ALTER TABLE instituciones ADD COLUMN ins_valoraciones_dias integer NOT NULL DEFAULT 180;
ALTER TABLE instituciones ADD CONSTRAINT ck_instituciones_valoraciones_dias
    CHECK (ins_valoraciones_dias BETWEEN 1 AND 730);


CREATE TABLE valoraciones (
    val_id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    val_ins_id      bigint       NOT NULL,
    val_codigo      varchar(16)  NOT NULL,
    val_est_id      bigint       NOT NULL,
    -- matricula del momento, para saber en que grado y grupo estaba
    val_mat_id      bigint       NOT NULL,
    val_psi_id      bigint       NOT NULL,
    -- confidencial: solo psicorientadores
    val_observacion text         NOT NULL,
    val_creado_en   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_valoraciones_tenant_id UNIQUE (val_ins_id, val_id),
    CONSTRAINT uq_valoraciones_codigo    UNIQUE (val_ins_id, val_codigo),
    CONSTRAINT fk_valoraciones_estudiante     FOREIGN KEY (val_ins_id, val_est_id) REFERENCES estudiantes (est_ins_id, est_id),
    -- la matricula tiene que ser del mismo estudiante
    CONSTRAINT fk_valoraciones_matricula      FOREIGN KEY (val_ins_id, val_est_id, val_mat_id)
        REFERENCES matriculas (mat_ins_id, mat_est_id, mat_id),
    CONSTRAINT fk_valoraciones_psicorientador FOREIGN KEY (val_ins_id, val_psi_id) REFERENCES personal (per_ins_id, per_id),
    CONSTRAINT ck_valoraciones_observacion CHECK (length(val_observacion) BETWEEN 5 AND 5000)
);

-- la ultima de cada estudiante
CREATE INDEX ix_valoraciones_estudiante ON valoraciones (val_ins_id, val_est_id, val_creado_en DESC);

ALTER TABLE valoraciones ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_valoraciones_tenant ON valoraciones
    USING (val_ins_id = tenant_actual()) WITH CHECK (val_ins_id = tenant_actual());

-- historial: solo se agrega
GRANT SELECT, INSERT ON valoraciones TO alertas_app;
