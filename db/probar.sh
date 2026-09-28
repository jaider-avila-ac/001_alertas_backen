#!/usr/bin/env bash
# corre pruebas/prueba_rls.sql, termina en rollback
set -euo pipefail
cd "$(dirname "$0")"
set -a; source .env; set +a
export MSYS_NO_PATHCONV=1
docker compose exec -T postgres psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" < pruebas/prueba_rls.sql
