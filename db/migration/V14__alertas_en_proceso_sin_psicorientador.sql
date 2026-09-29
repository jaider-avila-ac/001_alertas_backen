-- si se inactiva al psicorientador, sus casos abiertos vuelven a la bandeja sin asignar.
-- una alerta en proceso puede quedar un tiempo sin psicorientador hasta que otro la tome
ALTER TABLE alertas DROP CONSTRAINT ck_alertas_estado_psi;
