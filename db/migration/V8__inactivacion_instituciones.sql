-- V8: registro de la inhabilitación de una institución por parte del superadmin.
-- ins_activa (V2) ya bloquea el acceso; aquí se guarda cuándo y por qué, para mostrarlo
-- en el panel del superadmin. El historial completo (quién la habilitó o inhabilitó y cuándo)
-- queda en la bitácora con bit_sad_id.

ALTER TABLE instituciones
    ADD COLUMN ins_inactivada_en        timestamptz,
    ADD COLUMN ins_motivo_inactivacion  varchar(300);

-- Una institución inactiva siempre tiene fecha y motivo; una activa no tiene ninguno de los dos
ALTER TABLE instituciones
    ADD CONSTRAINT ck_instituciones_inactivacion CHECK (
        (ins_activa AND ins_inactivada_en IS NULL AND ins_motivo_inactivacion IS NULL)
        OR (NOT ins_activa AND ins_inactivada_en IS NOT NULL AND ins_motivo_inactivacion IS NOT NULL)
    );

-- El panel del superadmin filtra por estado
CREATE INDEX ix_instituciones_activa ON instituciones (ins_activa, ins_nombre);
