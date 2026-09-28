-- notificaciones, sms y bitacora

CREATE TABLE notificaciones (
    not_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    not_ins_id     bigint       NOT NULL,
    not_usu_id     bigint       NOT NULL,
    not_tipo       varchar(40)  NOT NULL,
    not_titulo     varchar(120) NOT NULL,
    not_mensaje    varchar(300) NOT NULL,
    -- ruta del front sin el slug
    not_enlace     varchar(200),
    not_leida      boolean      NOT NULL DEFAULT false,
    not_creado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_notificaciones_tenant_id UNIQUE (not_ins_id, not_id),
    CONSTRAINT fk_notificaciones_usuario FOREIGN KEY (not_ins_id, not_usu_id)
        REFERENCES usuarios (usu_ins_id, usu_id) ON DELETE CASCADE
);

CREATE INDEX ix_notificaciones_usuario ON notificaciones (not_ins_id, not_usu_id, not_leida, not_creado_en DESC);


-- un registro por sms, sirve para ver el costo por institucion
CREATE TABLE sms_envios (
    sms_id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sms_ins_id         bigint       NOT NULL,
    sms_evento         varchar(20)  NOT NULL,
    sms_destinatario   varchar(12)  NOT NULL,
    sms_celular        varchar(10)  NOT NULL,
    sms_mensaje        varchar(480) NOT NULL,
    sms_segmentos      smallint     NOT NULL DEFAULT 1,
    sms_estado         varchar(10)  NOT NULL DEFAULT 'PENDIENTE',
    sms_error          varchar(300),
    sms_proveedor_id   varchar(64),
    sms_ale_id         bigint,
    sms_cit_id         bigint,
    sms_creado_en      timestamptz  NOT NULL DEFAULT now(),
    sms_enviado_en     timestamptz,

    CONSTRAINT uq_sms_envios_tenant_id UNIQUE (sms_ins_id, sms_id),
    CONSTRAINT fk_sms_envios_alerta FOREIGN KEY (sms_ins_id, sms_ale_id) REFERENCES alertas (ale_ins_id, ale_id),
    CONSTRAINT fk_sms_envios_cita   FOREIGN KEY (sms_ins_id, sms_cit_id) REFERENCES citas (cit_ins_id, cit_id),
    CONSTRAINT ck_sms_envios_evento
        CHECK (sms_evento IN ('ALERTA_CREADA', 'CITA_AGENDADA', 'CITA_REPROGRAMADA', 'CITA_CANCELADA')),
    CONSTRAINT ck_sms_envios_destinatario CHECK (sms_destinatario IN ('ESTUDIANTE', 'FAMILIAR')),
    CONSTRAINT ck_sms_envios_estado CHECK (sms_estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    CONSTRAINT ck_sms_envios_celular CHECK (sms_celular ~ '^3[0-9]{9}$')
);

CREATE INDEX ix_sms_envios_fecha ON sms_envios (sms_ins_id, sms_creado_en);


-- acciones sensibles (ley 1581). solo insert y select
CREATE TABLE bitacora (
    bit_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bit_ins_id     bigint       NOT NULL REFERENCES instituciones (ins_id),
    -- o lo hizo un usuario o un superadmin
    bit_usu_id     bigint,
    bit_sad_id     bigint REFERENCES superadministradores (sad_id),
    bit_accion     varchar(60)  NOT NULL,
    bit_entidad    varchar(40)  NOT NULL,
    bit_entidad_id bigint,
    bit_detalle    text,
    bit_ip         varchar(45),
    bit_creado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_bitacora_usuario FOREIGN KEY (bit_ins_id, bit_usu_id) REFERENCES usuarios (usu_ins_id, usu_id),
    CONSTRAINT ck_bitacora_autor CHECK ((bit_usu_id IS NULL) <> (bit_sad_id IS NULL))
);

CREATE INDEX ix_bitacora_fecha   ON bitacora (bit_ins_id, bit_creado_en DESC);
CREATE INDEX ix_bitacora_entidad ON bitacora (bit_ins_id, bit_entidad, bit_entidad_id);
