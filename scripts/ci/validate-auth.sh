#!/usr/bin/env bash
set -euo pipefail

# This script creates only a disposable database in an isolated GitHub Actions runner.
[[ "${GITHUB_ACTIONS:-}" == "true" ]] || { echo 'Run this validation through GitHub Actions.'; exit 1; }
test_tmp=$(mktemp -d)
sql_container="work-journey-auth-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
backend_pid=''
proxy_pid=''
cleanup() {
  [[ -z "$proxy_pid" ]] || kill "$proxy_pid" 2>/dev/null || true
  [[ -z "$backend_pid" ]] || kill "$backend_pid" 2>/dev/null || true
  docker rm -f "$sql_container" >/dev/null 2>&1 || true
  rm -rf "$test_tmp"
}
trap cleanup EXIT

new_secret() { printf 'Aa1!%s' "$(openssl rand -hex 24)"; }
export MSSQL_SA_PASSWORD=$(new_secret)
export DB_MIGRATION_PASSWORD=$(new_secret)
export DB_PASSWORD=$(new_secret)
export EVALUATION_PASSWORD=$(new_secret)
for secret_name in MSSQL_SA_PASSWORD DB_MIGRATION_PASSWORD DB_PASSWORD EVALUATION_PASSWORD; do
  printf '::add-mask::%s\n' "${!secret_name}"
done
export DB_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=work_journey_ci;encrypt=true;trustServerCertificate=true'
export DB_USERNAME='journey_app'
export DB_MIGRATION_USERNAME='journey_migration'
export SERVER_ADDRESS='127.0.0.1'
export SERVER_SERVLET_SESSION_TIMEOUT='1m'

sql_image='mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04'
docker run -d --name "$sql_container" -p 127.0.0.1:1433:1433 \
  -e ACCEPT_EULA=Y -e MSSQL_PID=Developer -e MSSQL_SA_PASSWORD "$sql_image" >/dev/null
docker image inspect "$sql_image" --format '{{index .RepoDigests 0}}'
sql() {
  docker exec -i -e SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" "$sql_container" \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b "$@"
}
ready=false
for attempt in $(seq 1 60); do
  if sql -Q 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
[[ "$ready" == true ]] || { echo 'SQL Server did not become ready.'; exit 1; }

cat > "$test_tmp/provision.sql" <<SQL
CREATE DATABASE work_journey_ci;
GO
CREATE LOGIN journey_migration WITH PASSWORD = '$DB_MIGRATION_PASSWORD';
CREATE LOGIN journey_app WITH PASSWORD = '$DB_PASSWORD';
GO
USE work_journey_ci;
CREATE USER journey_migration FOR LOGIN journey_migration;
ALTER ROLE db_ddladmin ADD MEMBER journey_migration;
ALTER ROLE db_datareader ADD MEMBER journey_migration;
ALTER ROLE db_datawriter ADD MEMBER journey_migration;
CREATE USER journey_app FOR LOGIN journey_app;
GO
SQL
sql < "$test_tmp/provision.sql" >/dev/null
rm "$test_tmp/provision.sql"

# First create/validate schema and test mappings using the migration account.
# The subsequently running application uses a different account without DDL privileges.
(
  export DB_USERNAME="$DB_MIGRATION_USERNAME" DB_PASSWORD="$DB_MIGRATION_PASSWORD"
  cd backend
  ./mvnw --batch-mode -Dtest=SqlServerIT test
)
sql -d work_journey_ci -Q 'GRANT SELECT, INSERT ON dbo.app_user TO journey_app; GRANT SELECT, INSERT ON dbo.employee TO journey_app;' >/dev/null
(cd backend && ./mvnw --batch-mode verify)

start_backend() {
  java -jar backend/target/work-journey-backend-0.1.0-SNAPSHOT.jar \
    --spring.profiles.active=evaluation > "$test_tmp/backend.log" 2>&1 &
  backend_pid=$!
  ready=false
  for attempt in $(seq 1 60); do
    if curl -fsS http://127.0.0.1:8080/api/status >/dev/null 2>&1; then ready=true; break; fi
    if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if [[ "$ready" != true ]]; then
    cat "$test_tmp/backend.log"
    echo 'Backend failed to start with the runtime database account.'
    exit 1
  fi
}
start_backend
kill "$backend_pid"
wait "$backend_pid" || true
backend_pid=''
start_backend
sql -d work_journey_ci -Q "IF (SELECT COUNT(*) FROM dbo.app_user) <> 2 THROW 51000, 'Seed must create exactly two accounts across restarts', 1; IF (SELECT COUNT(*) FROM dbo.employee) <> 2 THROW 51001, 'Seed must create exactly two employees across restarts', 1; SELECT @@VERSION AS sql_server_version;"

export CI_TLS_KEY="$test_tmp/localhost.key"
export CI_TLS_CERT="$test_tmp/localhost.pem"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -keyout "$CI_TLS_KEY" -out "$CI_TLS_CERT" \
  -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost' >/dev/null 2>&1
node scripts/ci/https-proxy.mjs > "$test_tmp/proxy.log" 2>&1 &
proxy_pid=$!
ready=false
for attempt in $(seq 1 20); do
  if curl -kfsS https://localhost:8443/ >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'HTTPS test origin did not become ready.'; exit 1; }
(cd frontend && npx playwright test)
echo 'SQL Server mapping, repeated seed and real HTTPS browser authentication passed.'
