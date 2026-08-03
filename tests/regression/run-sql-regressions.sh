#!/usr/bin/env bash
# Runnable SQL regression replays for Project Draugr.
#
# Stands up a throwaway PostgreSQL via docker (the engine that is reachable on the dev machine
# even where Testcontainers is not), applies every migration in numeric order to prove the real
# schema builds, then runs each tests/regression/*.sql replay under ON_ERROR_STOP. Each replay
# RAISEs on the old-bug behaviour, so a regression exits this script non-zero.
#
# Usage:  tests/regression/run-sql-regressions.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
migrations="$repo/backend/src/main/resources/db/migration"
container="draugr_sql_regressions_$$"
port=55499

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== starting throwaway Postgres ($container) =="
docker run -d --name "$container" -e POSTGRES_PASSWORD=pw -e POSTGRES_DB=draugr -p "$port:5432" postgres:16 >/dev/null
for _ in $(seq 1 30); do docker exec "$container" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done

psql() { docker exec -i "$container" psql -U postgres -d draugr -v ON_ERROR_STOP=1 "$@"; }

echo "== applying every migration in numeric order =="
psql -q -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
for f in $(ls "$migrations"/V*.sql | sort -V); do
    psql -q -f - < "$f"
done
echo "   migrations applied clean."

echo "== running SQL regression replays =="
status=0
shopt -s nullglob
for replay in "$here"/*.sql; do
    name="$(basename "$replay")"
    echo "-- $name"
    if psql -q -f - < "$replay"; then
        echo "   OK: $name"
    else
        echo "   FAIL: $name"
        status=1
    fi
done

if [ "$status" -eq 0 ]; then echo "== ALL SQL REGRESSIONS PASSED =="; else echo "== SQL REGRESSIONS FAILED =="; fi
exit "$status"
