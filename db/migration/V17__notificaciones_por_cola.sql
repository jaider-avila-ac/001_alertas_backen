-- las notificaciones pasan por una cola (redis streams): la accion encola y un consumidor las guarda.
-- si el consumidor guarda pero se cae antes de confirmar, el mensaje se reintenta: con el id del
-- mensaje de la cola se sabe que ya estaba guardada y no se duplica
ALTER TABLE notificaciones ADD COLUMN not_cola_id varchar(40);

CREATE UNIQUE INDEX uq_notificaciones_cola ON notificaciones (not_ins_id, not_cola_id) WHERE not_cola_id IS NOT NULL;
