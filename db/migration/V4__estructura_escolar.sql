-- anios lectivos, grados, grupos y ubicaciones
-- los grupos no pasan de un anio a otro, cada anio tiene los suyos
-- y el estudiante tiene una ubicacion por anio

CREATE TABLE anios_lectivos (
    anl_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anl_ins_id     bigint      NOT NULL REFERENCES instituciones (ins_id),
    anl_anio       smallint    NOT NULL,
    anl_activo     boolean     NOT NULL DEFAULT false,
    anl_creado_en  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_anios_lectivos_tenant_id UNIQUE (anl_ins_id, anl_id),
    CONSTRAINT uq_anios_lectivos_anio      UNIQUE (anl_ins_id, anl_anio),
    CONSTRAINT ck_anios_lectivos_anio      CHECK (anl_anio BETWEEN 2000 AND 2100)
);

-- solo un anio activo
CREATE UNIQUE INDEX uq_anios_lectivos_activo ON anios_lectivos (anl_ins_id) WHERE anl_activo;


-- se cargan al crear la institucion, ella activa los que tenga
CREATE TABLE grados (
    gra_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gra_ins_id  bigint      NOT NULL REFERENCES instituciones (ins_id),
    gra_nombre  varchar(30) NOT NULL,
    -- -2 prejardin, -1 jardin, 0 transicion, 1 a 11
    gra_orden   smallint    NOT NULL,
    gra_activo  boolean     NOT NULL DEFAULT true,

    CONSTRAINT uq_grados_tenant_id UNIQUE (gra_ins_id, gra_id),
    CONSTRAINT uq_grados_orden     UNIQUE (gra_ins_id, gra_orden),
    CONSTRAINT ck_grados_orden     CHECK (gra_orden BETWEEN -2 AND 13)
);


CREATE TABLE grupos (
    grp_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grp_ins_id     bigint      NOT NULL,
    grp_anl_id     bigint      NOT NULL,
    grp_gra_id     bigint      NOT NULL,
    grp_nombre     varchar(20) NOT NULL,
    grp_creado_en  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_grupos_tenant_id      UNIQUE (grp_ins_id, grp_id),
    -- para la fk de ubicaciones (grupo del mismo anio)
    CONSTRAINT uq_grupos_tenant_anio_id UNIQUE (grp_ins_id, grp_anl_id, grp_id),
    CONSTRAINT fk_grupos_anio  FOREIGN KEY (grp_ins_id, grp_anl_id) REFERENCES anios_lectivos (anl_ins_id, anl_id),
    CONSTRAINT fk_grupos_grado FOREIGN KEY (grp_ins_id, grp_gra_id) REFERENCES grados (gra_ins_id, gra_id)
);

-- A y a son el mismo grupo
CREATE UNIQUE INDEX uq_grupos_nombre ON grupos (grp_ins_id, grp_anl_id, grp_gra_id, lower(grp_nombre));


-- en que grupo esta el estudiante cada anio
CREATE TABLE ubicaciones (
    ubi_id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ubi_ins_id          bigint      NOT NULL,
    ubi_est_id          bigint      NOT NULL,
    ubi_anl_id          bigint      NOT NULL,
    ubi_grp_id          bigint      NOT NULL,
    ubi_creado_en       timestamptz NOT NULL DEFAULT now(),
    ubi_actualizado_en  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_ubicaciones_tenant_id UNIQUE (ubi_ins_id, ubi_id),
    CONSTRAINT uq_ubicaciones_est_anio  UNIQUE (ubi_ins_id, ubi_est_id, ubi_anl_id),
    CONSTRAINT fk_ubicaciones_estudiante FOREIGN KEY (ubi_ins_id, ubi_est_id)
        REFERENCES estudiantes (est_ins_id, est_id),
    CONSTRAINT fk_ubicaciones_grupo FOREIGN KEY (ubi_ins_id, ubi_anl_id, ubi_grp_id)
        REFERENCES grupos (grp_ins_id, grp_anl_id, grp_id)
);

CREATE INDEX ix_ubicaciones_grupo ON ubicaciones (ubi_ins_id, ubi_grp_id);
CREATE INDEX ix_ubicaciones_anio  ON ubicaciones (ubi_ins_id, ubi_anl_id);
