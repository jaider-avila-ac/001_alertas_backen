-- V2: instituciones (los tenants) y superadministradores.
-- Ninguna de las dos tablas lleva RLS:
--  - instituciones debe poder leerse antes de conocer el tenant (el login resuelve slug -> id)
--    y no contiene datos sensibles;
--  - superadministradores no pertenece a ningún tenant.

CREATE TABLE instituciones (
    ins_id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ins_nombre              varchar(150) NOT NULL,
    ins_slug                varchar(60)  NOT NULL,
    ins_codigo_dane         varchar(20),
    ins_municipio           varchar(80),
    ins_departamento        varchar(80),
    ins_direccion           varchar(150),
    ins_telefono            varchar(20),
    ins_correo              varchar(120),
    ins_activa              boolean      NOT NULL DEFAULT true,
    -- Interruptor para cerrar el acceso de todos los estudiantes (ej. vacaciones)
    ins_acceso_estudiantes  boolean      NOT NULL DEFAULT true,
    ins_sms_activo          boolean      NOT NULL DEFAULT false,
    ins_creado_en           timestamptz  NOT NULL DEFAULT now(),
    ins_actualizado_en      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_instituciones_slug UNIQUE (ins_slug),
    -- El slug forma parte de la URL: minúsculas, números y guiones simples
    CONSTRAINT ck_instituciones_slug_formato
        CHECK (ins_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(ins_slug) BETWEEN 3 AND 60),
    -- Rutas propias del frontend que no pueden usarse como slug
    CONSTRAINT ck_instituciones_slug_reservado
        CHECK (ins_slug NOT IN ('superadmin', 'api', 'admin', 'login', 'assets', 'static', 'public'))
);

CREATE UNIQUE INDEX uq_instituciones_codigo_dane
    ON instituciones (ins_codigo_dane) WHERE ins_codigo_dane IS NOT NULL;
CREATE INDEX ix_instituciones_busqueda
    ON instituciones USING gin (lower(f_unaccent(ins_nombre || ' ' || ins_slug)) gin_trgm_ops);


CREATE TABLE superadministradores (
    sad_id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sad_usuario                 varchar(30)  NOT NULL,
    sad_nombres                 varchar(120) NOT NULL,
    sad_contrasena_hash         varchar(100) NOT NULL,
    sad_activo                  boolean      NOT NULL DEFAULT true,
    sad_contrasena_cambiada_en  timestamptz,
    sad_ultimo_ingreso          timestamptz,
    sad_creado_en               timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_superadministradores_usuario UNIQUE (sad_usuario)
);
