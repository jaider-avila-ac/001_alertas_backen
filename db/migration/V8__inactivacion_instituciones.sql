-- fecha y motivo cuando el superadmin inhabilita una institucion
-- el historial queda en la bitacora

ALTER TABLE instituciones
    ADD COLUMN ins_inactivada_en        timestamptz,
    ADD COLUMN ins_motivo_inactivacion  varchar(300);

-- inactiva siempre con fecha y motivo
ALTER TABLE instituciones
    ADD CONSTRAINT ck_instituciones_inactivacion CHECK (
        (ins_activa AND ins_inactivada_en IS NULL AND ins_motivo_inactivacion IS NULL)
        OR (NOT ins_activa AND ins_inactivada_en IS NOT NULL AND ins_motivo_inactivacion IS NOT NULL)
    );

CREATE INDEX ix_instituciones_activa ON instituciones (ins_activa, ins_nombre);
