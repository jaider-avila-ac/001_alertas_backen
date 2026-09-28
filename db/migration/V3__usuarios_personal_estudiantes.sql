-- usuarios, personal, estudiantes y familiares
--
-- cada tabla tiene unique (ins_id, id) y las fk van por (ins_id, id),
-- asi no se puede mezclar datos de dos instituciones aunque el backend falle

CREATE TABLE usuarios (
    usu_id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usu_ins_id                   bigint       NOT NULL REFERENCES instituciones (ins_id),
    -- numero de documento
    usu_usuario                  varchar(20)  NOT NULL,
    usu_contrasena_hash          varchar(100) NOT NULL,
    usu_rol                      varchar(20)  NOT NULL,
    usu_activo                   boolean      NOT NULL DEFAULT true,
    usu_debe_cambiar_contrasena  boolean      NOT NULL DEFAULT true,
    -- tokens anteriores a esta fecha ya no sirven
    usu_contrasena_cambiada_en   timestamptz,
    usu_ultimo_ingreso           timestamptz,
    usu_creado_en                timestamptz  NOT NULL DEFAULT now(),
    usu_actualizado_en           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_usuarios_tenant_id UNIQUE (usu_ins_id, usu_id),
    CONSTRAINT uq_usuarios_usuario   UNIQUE (usu_ins_id, usu_usuario),
    CONSTRAINT ck_usuarios_rol CHECK (usu_rol IN ('ADMIN', 'PSICORIENTADOR', 'DOCENTE', 'ESTUDIANTE'))
);

CREATE INDEX ix_usuarios_rol ON usuarios (usu_ins_id, usu_rol, usu_activo);


-- admin, docente y psicorientador. el rol esta en usuarios
CREATE TABLE personal (
    per_id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    per_ins_id          bigint       NOT NULL,
    per_usu_id          bigint       NOT NULL,
    per_tipo_doc        varchar(3)   NOT NULL,
    per_nro_doc         varchar(20)  NOT NULL,
    per_nombres         varchar(80)  NOT NULL,
    per_apellidos       varchar(80)  NOT NULL,
    per_correo          varchar(120),
    per_celular         varchar(10),
    per_busqueda        text GENERATED ALWAYS AS
                            (lower(f_unaccent(per_nombres || ' ' || per_apellidos || ' ' || per_nro_doc))) STORED,
    per_creado_en       timestamptz  NOT NULL DEFAULT now(),
    per_actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_personal_tenant_id UNIQUE (per_ins_id, per_id),
    CONSTRAINT uq_personal_nro_doc   UNIQUE (per_ins_id, per_nro_doc),
    CONSTRAINT uq_personal_usuario   UNIQUE (per_ins_id, per_usu_id),
    CONSTRAINT fk_personal_usuario FOREIGN KEY (per_ins_id, per_usu_id) REFERENCES usuarios (usu_ins_id, usu_id),
    CONSTRAINT ck_personal_tipo_doc CHECK (per_tipo_doc IN ('RC', 'TI', 'CC', 'CE', 'PPT')),
    CONSTRAINT ck_personal_nro_doc  CHECK (per_nro_doc ~ '^[A-Za-z0-9]{3,20}$'),
    CONSTRAINT ck_personal_celular  CHECK (per_celular ~ '^3[0-9]{9}$')
);

CREATE INDEX ix_personal_busqueda ON personal USING gin (per_busqueda gin_trgm_ops);
CREATE INDEX ix_personal_orden    ON personal (per_ins_id, per_apellidos, per_nombres);


CREATE TABLE estudiantes (
    est_id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    est_ins_id           bigint       NOT NULL,
    est_usu_id           bigint       NOT NULL,
    est_tipo_doc         varchar(3)   NOT NULL,
    -- unico sin importar el tipo, de RC a TI el numero no cambia
    est_nro_doc          varchar(20)  NOT NULL,
    est_nombres          varchar(80)  NOT NULL,
    est_apellidos        varchar(80)  NOT NULL,
    est_genero           char(1),
    est_fecha_nacimiento date,
    est_celular          varchar(10),
    -- el psicorientador lo puede apagar, ej violencia intrafamiliar
    est_sms_familiares   boolean      NOT NULL DEFAULT true,
    est_busqueda         text GENERATED ALWAYS AS
                             (lower(f_unaccent(est_nombres || ' ' || est_apellidos || ' ' || est_nro_doc))) STORED,
    est_creado_en        timestamptz  NOT NULL DEFAULT now(),
    est_actualizado_en   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_estudiantes_tenant_id UNIQUE (est_ins_id, est_id),
    CONSTRAINT uq_estudiantes_nro_doc   UNIQUE (est_ins_id, est_nro_doc),
    CONSTRAINT uq_estudiantes_usuario   UNIQUE (est_ins_id, est_usu_id),
    CONSTRAINT fk_estudiantes_usuario FOREIGN KEY (est_ins_id, est_usu_id) REFERENCES usuarios (usu_ins_id, usu_id),
    CONSTRAINT ck_estudiantes_tipo_doc CHECK (est_tipo_doc IN ('RC', 'TI', 'CC', 'CE', 'PPT')),
    CONSTRAINT ck_estudiantes_nro_doc  CHECK (est_nro_doc ~ '^[A-Za-z0-9]{3,20}$'),
    CONSTRAINT ck_estudiantes_genero   CHECK (est_genero IN ('F', 'M', 'O')),
    CONSTRAINT ck_estudiantes_celular  CHECK (est_celular ~ '^3[0-9]{9}$')
);

CREATE INDEX ix_estudiantes_busqueda ON estudiantes USING gin (est_busqueda gin_trgm_ops);
CREATE INDEX ix_estudiantes_orden    ON estudiantes (est_ins_id, est_apellidos, est_nombres);


-- maximo 3 familiares: posicion 1 a 3 y unica por estudiante
CREATE TABLE familiares (
    fam_id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fam_ins_id          bigint       NOT NULL,
    fam_est_id          bigint       NOT NULL,
    fam_posicion        smallint     NOT NULL,
    fam_nombres         varchar(80)  NOT NULL,
    fam_apellidos       varchar(80),
    fam_parentesco      varchar(20)  NOT NULL,
    fam_celular         varchar(10),
    fam_recibe_sms      boolean      NOT NULL DEFAULT true,
    fam_creado_en       timestamptz  NOT NULL DEFAULT now(),
    fam_actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_familiares_tenant_id UNIQUE (fam_ins_id, fam_id),
    CONSTRAINT uq_familiares_posicion  UNIQUE (fam_est_id, fam_posicion),
    CONSTRAINT fk_familiares_estudiante FOREIGN KEY (fam_ins_id, fam_est_id)
        REFERENCES estudiantes (est_ins_id, est_id) ON DELETE CASCADE,
    CONSTRAINT ck_familiares_posicion  CHECK (fam_posicion BETWEEN 1 AND 3),
    CONSTRAINT ck_familiares_parentesco
        CHECK (fam_parentesco IN ('MADRE', 'PADRE', 'ACUDIENTE', 'ABUELO', 'HERMANO', 'TIO', 'OTRO')),
    CONSTRAINT ck_familiares_celular   CHECK (fam_celular ~ '^3[0-9]{9}$'),
    CONSTRAINT ck_familiares_sms_celular CHECK (NOT fam_recibe_sms OR fam_celular IS NOT NULL)
);

CREATE INDEX ix_familiares_estudiante ON familiares (fam_ins_id, fam_est_id);
