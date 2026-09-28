-- extensiones y funciones base
-- las tres son trusted, alertas_owner las puede crear sin ser superusuario

CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- busqueda por nombre
CREATE EXTENSION IF NOT EXISTS unaccent;  -- buscar sin tildes
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- para que no se crucen citas

-- unaccent es STABLE y no deja usarla en indices ni columnas generadas, por eso este wrapper
CREATE OR REPLACE FUNCTION f_unaccent(texto text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, texto) $$;

-- tenant de la transaccion, lo pone el backend con set_config(..., true)
-- si no hay tenant da NULL y RLS no deja ver nada
CREATE OR REPLACE FUNCTION tenant_actual() RETURNS bigint
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::bigint $$;
