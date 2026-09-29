-- la hora de la cita es solo una referencia: el psicorientador la inicia cuando quiera.
-- dentro de la cita escribe una observacion por cada alerta (la observacion general deja de usarse)

-- programada + iniciada_en = en curso
ALTER TABLE citas ADD COLUMN cit_iniciada_en timestamptz;

-- lo que se hablo de esa alerta en esa cita. confidencial: solo psicorientadores
ALTER TABLE citas_alertas ADD COLUMN cia_observacion text;

-- las citas cerradas antes de este cambio: cada alerta se queda con la observacion general de su cita
UPDATE citas_alertas ca SET cia_observacion = coalesce(c.cit_observacion, 'Sin observacion')
FROM citas c
WHERE c.cit_id = ca.cia_cit_id AND ca.cia_resultado IS NOT NULL AND ca.cia_observacion IS NULL;

ALTER TABLE citas_alertas ADD CONSTRAINT ck_citas_alertas_observacion
    CHECK (cia_resultado IS NULL OR cia_observacion IS NOT NULL);
