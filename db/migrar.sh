#!/usr/bin/env bash
# Aplica, en orden, las migraciones de db/migration que aún no se han aplicado.
# Se ejecuta en el servidor de base de datos, dentro de la carpeta db/.
# Cada script corre en una sola transacción con el rol alertas_owner (dueño de las tablas)
# y queda registrado en esquema_migraciones. Se puede ejecutar las veces que sea: si todo
# está aplicado, no hace nada.
#
# Uso: ./migrar.sh            (aplica las pendientes)
#      ./migrar.sh --estado   (solo muestra cuáles faltan)
set -euo pipefail
cd "$(dirname "$0")"

set -a; source .env; set +a
export MSYS_NO_PATHCONV=1   # evita que Git Bash en Windows reescriba rutas

psql_owner() {
  docker compose exec -T -e PGPASSWORD="$DB_OWNER_PASSWORD" -e PGOPTIONS="-c client_min_messages=warning" postgres \
    psql -v ON_ERROR_STOP=1 -q -h localhost -U alertas_owner -d "$POSTGRES_DB" "$@"
}

psql_owner -c "CREATE TABLE IF NOT EXISTS esquema_migraciones (
  version varchar(100) PRIMARY KEY, aplicado_en timestamptz NOT NULL DEFAULT now())" >/dev/null

aplicadas=$(psql_owner -At -c "SELECT version FROM esquema_migraciones")
pendientes=0

for archivo in $(ls migration/V*__*.sql | sort -V); do
  version=$(basename "$archivo" .sql)
  if grep -qx "$version" <<<"$aplicadas"; then continue; fi
  pendientes=$((pendientes + 1))
  if [[ "${1:-}" == "--estado" ]]; then echo "PENDIENTE  $version"; continue; fi
  echo "Aplicando  $version"
  { cat "$archivo"; echo; echo "INSERT INTO esquema_migraciones (version) VALUES ('$version');"; } \
    | psql_owner -1
done

if [[ $pendientes -eq 0 ]]; then echo "La base de datos está al día."; fi
