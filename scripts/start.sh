#!/usr/bin/env bash
#
# One-command startup for Linux and macOS.
#
# Builds the jar if needed, starts the application in the background, waits for it to become
# healthy, and prints the URLs. Use scripts/stop.sh to shut it down.
#
# Usage:
#   ./scripts/start.sh              start in the background
#   ./scripts/start.sh --foreground run in the foreground (Ctrl-C to stop)
#   ./scripts/start.sh --clean      delete both H2 databases first, so migrations re-run from scratch
#
# Author: Wallace Espindola

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

PORT="${SERVER_PORT:-8080}"
JAR="target/flyway-vs-liquibase-db-migrations.jar"
PID_FILE=".run/app.pid"
LOG_FILE=".run/app.log"
FOREGROUND=false
CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --foreground|-f) FOREGROUND=true ;;
        --clean|-c)      CLEAN=true ;;
        --help|-h)
            sed -n '3,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
    esac
done

if command -v mvn >/dev/null 2>&1; then
    MVN=mvn
elif [[ -x ./mvnw ]]; then
    MVN=./mvnw
else
    echo "ERROR: Maven not found. Install Maven or add the Maven wrapper." >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found on PATH. Java 21 or newer is required." >&2
    exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] && (( JAVA_MAJOR < 21 )); then
    echo "ERROR: Java 21 or newer is required, found Java $JAVA_MAJOR." >&2
    exit 1
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Application is already running (PID $(cat "$PID_FILE")). Run scripts/stop.sh first."
    exit 1
fi

if $CLEAN; then
    echo "==> Removing H2 databases so both engines migrate from scratch"
    rm -rf data
fi

echo "==> Building $JAR"
"$MVN" -B -q -DskipTests package

mkdir -p .run

if $FOREGROUND; then
    echo "==> Starting in the foreground on port $PORT (Ctrl-C to stop)"
    exec java -jar "$JAR" --server.port="$PORT"
fi

echo "==> Starting in the background on port $PORT"
nohup java -jar "$JAR" --server.port="$PORT" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

printf '==> Waiting for the application to become healthy'
for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:${PORT}/api/v1/health" >/dev/null 2>&1; then
        echo
        echo "==> Ready."
        echo
        echo "  Dashboard    http://localhost:${PORT}/"
        echo "  Swagger UI   http://localhost:${PORT}/swagger-ui.html"
        echo "  Comparison   http://localhost:${PORT}/api/v1/comparison"
        echo "  H2 console   http://localhost:${PORT}/h2-console"
        echo "  Logs         $LOG_FILE"
        echo
        echo "  Stop with:   ./scripts/stop.sh"
        exit 0
    fi
    if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo
        echo "ERROR: the application exited during startup. Last 40 log lines:" >&2
        tail -40 "$LOG_FILE" >&2
        rm -f "$PID_FILE"
        exit 1
    fi
    printf '.'
    sleep 1
done

echo
echo "ERROR: timed out waiting for health. Last 40 log lines:" >&2
tail -40 "$LOG_FILE" >&2
exit 1
