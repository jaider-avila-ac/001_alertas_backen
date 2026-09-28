-- categorias, alertas y citas
-- cada alerta tiene su estado. una cita atiende todas las activas del estudiante
-- y al cerrarla se decide cual sigue en proceso y cual se completa

CREATE TABLE categorias_alerta (
    cat_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cat_ins_id     bigint      NOT NULL REFERENCES instituciones (ins_id),
    cat_nombre     varchar(80) NOT NULL,
    cat_activa     boolean     NOT NULL DEFAULT true,
    cat_creado_en  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_categorias_alerta_tenant_id UNIQUE (cat_ins_id, cat_id)
);

-- que no se repita con otra mayuscula o tilde
CREATE UNIQUE INDEX uq_categorias_alerta_nombre ON categorias_alerta (cat_ins_id, lower(f_unaccent(cat_nombre)));


CREATE TABLE alertas (
    ale_id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ale_ins_id                   bigint       NOT NULL,
    ale_est_id                   bigint       NOT NULL,
    -- ESTUDIANTE = solicitud de ayuda
    ale_origen                   varchar(10)  NOT NULL,
    ale_reportada_por            bigint       NOT NULL,  -- usuario
    ale_cat_id                   bigint       NOT NULL,
    -- urgencia del estudiante: baja=LEVE, media=MODERADO, alta=ALTO
    ale_nivel                    varchar(10)  NOT NULL,
    ale_descripcion              text         NOT NULL,
    ale_fecha_hecho              date,
    ale_lugar                    varchar(150),
    ale_peligro_inmediato        boolean      NOT NULL DEFAULT false,
    -- solo solicitudes del estudiante
    ale_horario_seguro           varchar(150),
    ale_modalidad_preferida      varchar(10),
    ale_autoriza_sms_familiares  boolean,
    -- anio y grupo del momento, para que las estadisticas viejas no cambien
    ale_anl_id                   bigint       NOT NULL,
    ale_grp_id                   bigint,
    ale_estado                   varchar(12)  NOT NULL DEFAULT 'PENDIENTE',
    ale_psi_id                   bigint,  -- psicorientador (personal)
    ale_asignada_en              timestamptz,
    ale_conclusion               text,
    ale_completada_en            timestamptz,
    ale_prioritaria              boolean GENERATED ALWAYS AS (
                                     ale_nivel = 'CRITICO'
                                     OR ale_peligro_inmediato
                                     OR (ale_origen = 'ESTUDIANTE' AND ale_nivel = 'ALTO')) STORED,
    ale_version                  integer      NOT NULL DEFAULT 0,
    ale_creado_en                timestamptz  NOT NULL DEFAULT now(),
    ale_actualizado_en           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_alertas_tenant_id     UNIQUE (ale_ins_id, ale_id),
    -- para la fk de citas_alertas
    CONSTRAINT uq_alertas_tenant_est_id UNIQUE (ale_ins_id, ale_est_id, ale_id),
    CONSTRAINT fk_alertas_estudiante FOREIGN KEY (ale_ins_id, ale_est_id)        REFERENCES estudiantes (est_ins_id, est_id),
    CONSTRAINT fk_alertas_reporta    FOREIGN KEY (ale_ins_id, ale_reportada_por) REFERENCES usuarios (usu_ins_id, usu_id),
    CONSTRAINT fk_alertas_categoria  FOREIGN KEY (ale_ins_id, ale_cat_id)        REFERENCES categorias_alerta (cat_ins_id, cat_id),
    CONSTRAINT fk_alertas_anio       FOREIGN KEY (ale_ins_id, ale_anl_id)        REFERENCES anios_lectivos (anl_ins_id, anl_id),
    CONSTRAINT fk_alertas_grupo      FOREIGN KEY (ale_ins_id, ale_grp_id)        REFERENCES grupos (grp_ins_id, grp_id),
    CONSTRAINT fk_alertas_psicorientador FOREIGN KEY (ale_ins_id, ale_psi_id)    REFERENCES personal (per_ins_id, per_id),
    CONSTRAINT ck_alertas_origen    CHECK (ale_origen IN ('DOCENTE', 'ESTUDIANTE')),
    CONSTRAINT ck_alertas_nivel     CHECK (ale_nivel IN ('LEVE', 'MODERADO', 'ALTO', 'CRITICO')),
    CONSTRAINT ck_alertas_estado    CHECK (ale_estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA')),
    CONSTRAINT ck_alertas_modalidad CHECK (ale_modalidad_preferida IN ('PRESENCIAL', 'VIRTUAL')),
    CONSTRAINT ck_alertas_descripcion CHECK (length(ale_descripcion) BETWEEN 10 AND 5000),
    CONSTRAINT ck_alertas_campos_estudiante CHECK (
        ale_origen = 'ESTUDIANTE'
        OR (ale_horario_seguro IS NULL AND ale_modalidad_preferida IS NULL AND ale_autoriza_sms_familiares IS NULL)),
    CONSTRAINT ck_alertas_estado_psi CHECK (ale_estado = 'PENDIENTE' OR ale_psi_id IS NOT NULL),
    CONSTRAINT ck_alertas_completada CHECK ((ale_estado = 'COMPLETADA') = (ale_completada_en IS NOT NULL))
);

-- bandeja
CREATE INDEX ix_alertas_bandeja    ON alertas (ale_ins_id, ale_prioritaria DESC, ale_creado_en)
    WHERE ale_psi_id IS NULL AND ale_estado = 'PENDIENTE';
CREATE INDEX ix_alertas_psi_estado ON alertas (ale_ins_id, ale_psi_id, ale_estado);
CREATE INDEX ix_alertas_estudiante ON alertas (ale_ins_id, ale_est_id, ale_estado);
CREATE INDEX ix_alertas_reporta    ON alertas (ale_ins_id, ale_reportada_por, ale_creado_en DESC);
CREATE INDEX ix_alertas_fecha      ON alertas (ale_ins_id, ale_creado_en);
CREATE INDEX ix_alertas_grupo      ON alertas (ale_ins_id, ale_anl_id, ale_grp_id);
CREATE INDEX ix_alertas_categoria  ON alertas (ale_ins_id, ale_cat_id);


CREATE TABLE citas (
    cit_id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cit_ins_id             bigint       NOT NULL,
    cit_est_id             bigint       NOT NULL,
    cit_psi_id             bigint       NOT NULL,
    cit_inicio             timestamptz  NOT NULL,
    cit_fin                timestamptz  NOT NULL,
    cit_modalidad          varchar(10)  NOT NULL,
    cit_lugar              varchar(200),
    -- la ve el estudiante
    cit_indicacion         varchar(500),
    cit_estado             varchar(12)  NOT NULL DEFAULT 'PROGRAMADA',
    -- solo psicorientadores
    cit_observacion        text,
    cit_motivo_cancelacion varchar(300),
    cit_cerrada_en         timestamptz,
    cit_creada_por         bigint       NOT NULL,
    cit_version            integer      NOT NULL DEFAULT 0,
    cit_creado_en          timestamptz  NOT NULL DEFAULT now(),
    cit_actualizado_en     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_citas_tenant_id     UNIQUE (cit_ins_id, cit_id),
    CONSTRAINT uq_citas_tenant_est_id UNIQUE (cit_ins_id, cit_est_id, cit_id),
    CONSTRAINT fk_citas_estudiante     FOREIGN KEY (cit_ins_id, cit_est_id)     REFERENCES estudiantes (est_ins_id, est_id),
    CONSTRAINT fk_citas_psicorientador FOREIGN KEY (cit_ins_id, cit_psi_id)     REFERENCES personal (per_ins_id, per_id),
    CONSTRAINT fk_citas_creada_por     FOREIGN KEY (cit_ins_id, cit_creada_por) REFERENCES usuarios (usu_ins_id, usu_id),
    CONSTRAINT ck_citas_rango     CHECK (cit_fin > cit_inicio),
    CONSTRAINT ck_citas_modalidad CHECK (cit_modalidad IN ('PRESENCIAL', 'VIRTUAL')),
    CONSTRAINT ck_citas_estado    CHECK (cit_estado IN ('PROGRAMADA', 'REALIZADA', 'NO_ASISTIO', 'CANCELADA')),
    CONSTRAINT ex_citas_cruce_psicorientador EXCLUDE USING gist (
        cit_ins_id WITH =, cit_psi_id WITH =, tstzrange(cit_inicio, cit_fin) WITH &&
    ) WHERE (cit_estado IN ('PROGRAMADA', 'REALIZADA'))
);

-- una sola cita programada por estudiante, las alertas nuevas se pegan a esa
CREATE UNIQUE INDEX uq_citas_programada_estudiante ON citas (cit_ins_id, cit_est_id) WHERE cit_estado = 'PROGRAMADA';
CREATE INDEX ix_citas_agenda ON citas (cit_ins_id, cit_psi_id, cit_inicio);
CREATE INDEX ix_citas_estudiante ON citas (cit_ins_id, cit_est_id, cit_inicio DESC);


-- alertas que se vieron en cada cita
CREATE TABLE citas_alertas (
    cia_ins_id     bigint      NOT NULL,
    cia_cit_id     bigint      NOT NULL,
    cia_ale_id     bigint      NOT NULL,
    cia_est_id     bigint      NOT NULL,
    -- null hasta cerrar la cita
    cia_resultado  varchar(12),

    CONSTRAINT pk_citas_alertas PRIMARY KEY (cia_cit_id, cia_ale_id),
    -- misma institucion y mismo estudiante
    CONSTRAINT fk_citas_alertas_cita   FOREIGN KEY (cia_ins_id, cia_est_id, cia_cit_id)
        REFERENCES citas (cit_ins_id, cit_est_id, cit_id) ON DELETE CASCADE,
    CONSTRAINT fk_citas_alertas_alerta FOREIGN KEY (cia_ins_id, cia_est_id, cia_ale_id)
        REFERENCES alertas (ale_ins_id, ale_est_id, ale_id),
    CONSTRAINT ck_citas_alertas_resultado CHECK (cia_resultado IN ('EN_PROCESO', 'COMPLETADA'))
);

CREATE INDEX ix_citas_alertas_alerta ON citas_alertas (cia_ins_id, cia_ale_id);
