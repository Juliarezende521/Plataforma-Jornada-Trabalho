#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SA_PASSWORD="${MSSQL_SA_PASSWORD:-LocalSqlServer@2026}"
SQLSERVER_PORT="${SQLSERVER_PORT:-1433}"
DB_URL="jdbc:sqlserver://localhost:${SQLSERVER_PORT};databaseName=work_journey;encrypt=true;trustServerCertificate=true"
MIGRATOR_PASSWORD="LocalMigrator@2026"

cd "$PROJECT_DIR"
MSSQL_SA_PASSWORD="$SA_PASSWORD" SQLSERVER_PORT="$SQLSERVER_PORT" docker compose up -d --wait sqlserver

docker compose exec -T \
  -e SQLCMDPASSWORD="$SA_PASSWORD" \
  sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b \
  -i /dev/stdin < scripts/local/database.sql

echo "Aplicando migrations..."
MIGRATION_LOG="$(mktemp -t work-journey-migration.XXXXXX)"
DB_URL="$DB_URL" \
DB_USERNAME=work_journey_migrator \
DB_PASSWORD="$MIGRATOR_PASSWORD" \
DB_MIGRATION_USERNAME=work_journey_migrator \
DB_MIGRATION_PASSWORD="$MIGRATOR_PASSWORD" \
SERVER_ADDRESS=127.0.0.1 \
  backend/mvnw -f backend/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=0 >"$MIGRATION_LOG" 2>&1 &
MIGRATION_PID=$!

cleanup() {
  if kill -0 "$MIGRATION_PID" 2>/dev/null; then
    kill "$MIGRATION_PID" 2>/dev/null || true
    wait "$MIGRATION_PID" 2>/dev/null || true
  fi
  rm -f "$MIGRATION_LOG"
}
trap cleanup EXIT INT TERM

for _ in {1..120}; do
  if grep -q "Started WorkJourneyApplication" "$MIGRATION_LOG"; then
    break
  fi
  if ! kill -0 "$MIGRATION_PID" 2>/dev/null; then
    echo "Não foi possível aplicar as migrations:" >&2
    tail -n 40 "$MIGRATION_LOG" >&2
    exit 1
  fi
  sleep 1
done

if ! grep -q "Started WorkJourneyApplication" "$MIGRATION_LOG"; then
  echo "Tempo esgotado ao aguardar as migrations." >&2
  tail -n 40 "$MIGRATION_LOG" >&2
  exit 1
fi

kill "$MIGRATION_PID"
wait "$MIGRATION_PID" 2>/dev/null || true
rm -f "$MIGRATION_LOG"
trap - EXIT INT TERM

docker compose exec -T \
  -e SQLCMDPASSWORD="$SA_PASSWORD" \
  sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b \
  -i /dev/stdin < scripts/local/grants.sql

echo "Banco preparado. Execute ./scripts/local/backend.sh e ./scripts/local/frontend.sh."
