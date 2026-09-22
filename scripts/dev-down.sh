#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for service in web api; do
  PID_FILE="${ROOT_DIR}/.runtime/pids/${service}.pid"
  if [[ -f "${PID_FILE}" ]]; then
    PID="$(cat "${PID_FILE}")"
    if kill -0 "${PID}" 2>/dev/null; then kill "${PID}"; fi
    rm -f "${PID_FILE}"
  fi
done
SEATUNNEL_HOME="${ROOT_DIR}/.runtime/apache-seatunnel-2.3.13"
if [[ -x "${SEATUNNEL_HOME}/bin/stop-seatunnel-cluster.sh" ]]; then
  (cd "${SEATUNNEL_HOME}" && sh bin/stop-seatunnel-cluster.sh) || true
fi
echo "Local services stopped."
