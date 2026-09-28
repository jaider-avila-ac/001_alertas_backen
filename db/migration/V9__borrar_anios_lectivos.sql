-- el admin puede borrar un anio creado por error (solo si no esta activo y no tiene grupos,
-- eso lo valida el backend y la fk de grupos)
GRANT DELETE ON anios_lectivos TO alertas_app;
