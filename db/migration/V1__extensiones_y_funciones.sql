-- V1: extensiones y funciones base.
-- Se aplica con el rol alertas_owner. Las tres extensiones son "trusted" (PG13+),
-- así que el dueño de la base puede crearlas sin ser superusuario.

CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- búsqueda aproximada por nombre (índices GIN)
CREATE EXTENSION IF NOT EXISTS unaccent;    -- búsqueda sin tildes
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- evitar citas cruzadas del mismo psicorientador

-- unaccent() está marcada como STABLE y PostgreSQL no permite usarla en columnas generadas
-- ni en índices. Esta versión fija el diccionario, por lo que sí es segura como IMMUTABLE.
CREATE OR REPLACE FUNCTION f_unaccent(texto text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, texto) $$;

-- Tenant de la transacción actual. La aplicación lo fija con
-- set_config('app.current_tenant_id', <id>, true) al inicio de cada transacción.
-- Si no está fijado devuelve NULL, y como NULL nunca es igual a nada, las políticas RLS
-- no dejan ver ni escribir ninguna fila.
CREATE OR REPLACE FUNCTION tenant_actual() RETURNS bigint
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::bigint $$;
