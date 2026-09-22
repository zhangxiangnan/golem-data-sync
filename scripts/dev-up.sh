#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime"
mkdir -p "${RUNTIME_DIR}/logs" "${RUNTIME_DIR}/pids"
if [[ -f "${ROOT_DIR}/.env" ]]; then set -a; source "${ROOT_DIR}/.env"; set +a; fi

"${ROOT_DIR}/scripts/start-seatunnel.sh"
if [[ ! -d "${ROOT_DIR}/apps/web/node_modules" ]]; then (cd "${ROOT_DIR}/apps/web" && pnpm install); fi

MAVEN="mvn"
if [[ -x "${ROOT_DIR}/mvnw" ]]; then MAVEN="${ROOT_DIR}/mvnw"; fi
(cd "${ROOT_DIR}" && nohup "${MAVEN}" spring-boot:run >"${RUNTIME_DIR}/logs/api.log" 2>&1 & echo $! >"${RUNTIME_DIR}/pids/api.pid")
(cd "${ROOT_DIR}/apps/web" && nohup pnpm dev >"${RUNTIME_DIR}/logs/web.log" 2>&1 & echo $! >"${RUNTIME_DIR}/pids/web.pid")

echo "Services are starting:"
echo "  Web        http://127.0.0.1:3200"
echo "  API        http://127.0.0.1:8090"
echo "  SeaTunnel  http://127.0.0.1:8081"
echo "Logs: ${RUNTIME_DIR}/logs"
