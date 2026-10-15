#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQLSERVER_PORT="${SQLSERVER_PORT:-1433}"
export DB_URL="${DB_URL:-jdbc:sqlserver://localhost:${SQLSERVER_PORT};databaseName=work_journey;encrypt=true;trustServerCertificate=true}"
export DB_USERNAME="${DB_USERNAME:-work_journey_app}"
export DB_PASSWORD="${DB_PASSWORD:-LocalApplication@2026}"
export DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-work_journey_migrator}"
export DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-LocalMigrator@2026}"
export EVALUATION_PASSWORD="${EVALUATION_PASSWORD:-AvaliacaoLocal@2026}"
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"

exec "$PROJECT_DIR/backend/mvnw" -f "$PROJECT_DIR/backend/pom.xml" \
  spring-boot:run -Dspring-boot.run.profiles=evaluation
