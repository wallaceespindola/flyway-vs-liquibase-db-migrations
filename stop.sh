#!/usr/bin/env bash
#
# Stops the application started by start.sh on Linux and macOS.
#
# Usage:
#   ./stop.sh          graceful shutdown
#   ./stop.sh --clean  also delete both H2 databases
#
# Author: Wallace Espindola

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

PID_FILE=".run/app.pid"
CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --clean|-c) CLEAN=true ;;
        --help|-h)  sed -n '3,8p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
    esac
done

# A pid file can outlive its process (OOM kill, kill -9) and the OS recycles pids, so never signal a
# pid without first confirming it is actually this application.
is_our_process() {
    local pid="$1"
    kill -0 "$pid" 2>/dev/null || return 1
    ps -p "$pid" -o command= 2>/dev/null | grep -q 'flyway-vs-liquibase-db-migrations.jar'
}

stop_pid() {
    local pid="$1"
    echo "==> Stopping PID $pid"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do
        kill -0 "$pid" 2>/dev/null || { echo "==> Stopped."; return 0; }
        sleep 0.5
    done
    echo "==> Still running after 10s, sending SIGKILL"
    kill -9 "$pid" 2>/dev/null || true
}

if [[ -f "$PID_FILE" ]]; then
    PID="$(cat "$PID_FILE")"
    if is_our_process "$PID"; then
        stop_pid "$PID"
    else
        echo "==> PID $PID is not this application, cleaning up stale pid file without signalling it"
    fi
    rm -f "$PID_FILE"
else
    # Fall back to finding the process by its jar name, for runs not started by start.sh.
    PIDS="$(pgrep -f 'flyway-vs-liquibase-db-migrations.jar' || true)"
    if [[ -n "$PIDS" ]]; then
        for pid in $PIDS; do stop_pid "$pid"; done
    else
        echo "==> Application is not running."
    fi
fi

if $CLEAN; then
    echo "==> Removing H2 databases"
    rm -rf data
fi
