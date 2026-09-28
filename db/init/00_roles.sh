#!/bin/bash
# Crea los dos roles del sistema. Se ejecuta una sola vez, cuando el volumen de datos está vacío.
#  - alertas_owner: dueño del esquema y de las tablas; con él se aplican las migraciones.
#  - alertas_app:   rol de la aplicación. Al no ser dueño de las tablas, PostgreSQL le aplica RLS.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v dbname="$POSTGRES_DB" -v owner_pass="$DB_OWNER_PASSWORD" -v app_pass="$DB_APP_PASSWORD" <<-'EOSQL'
	CREATE ROLE alertas_owner LOGIN PASSWORD :'owner_pass';
	CREATE ROLE alertas_app   LOGIN PASSWORD :'app_pass';

	ALTER DATABASE :"dbname" OWNER TO alertas_owner;
	REVOKE ALL ON DATABASE :"dbname" FROM PUBLIC;
	GRANT CONNECT ON DATABASE :"dbname" TO alertas_app;

	ALTER SCHEMA public OWNER TO alertas_owner;
	REVOKE CREATE ON SCHEMA public FROM PUBLIC;
	GRANT USAGE ON SCHEMA public TO alertas_app;
EOSQL
