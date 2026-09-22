#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEATUNNEL_HOME="${ROOT_DIR}/.runtime/apache-seatunnel-2.3.13"
if [[ ! -x "${SEATUNNEL_HOME}/bin/seatunnel-cluster.sh" ]]; then
  echo "SeaTunnel is not installed. Run ./scripts/bootstrap-seatunnel.sh first." >&2
  exit 1
fi
if curl --silent --fail http://127.0.0.1:8081/overview >/dev/null 2>&1; then
  echo "SeaTunnel is already running on port 8081."
  exit 0
fi
export SEATUNNEL_HOME
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"
(cd "${SEATUNNEL_HOME}" && sh bin/seatunnel-cluster.sh -d)
echo "Waiting for SeaTunnel REST API..."
for _ in $(seq 1 60); do
  if curl --silent --fail http://127.0.0.1:8081/overview >/dev/null 2>&1; then
    echo "SeaTunnel is running: http://127.0.0.1:8081"
    exit 0
  fi
  sleep 1
done
echo "SeaTunnel did not become ready. Check ${SEATUNNEL_HOME}/logs." >&2
exit 1
