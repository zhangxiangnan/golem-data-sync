#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="2.3.13"
RUNTIME_DIR="${ROOT_DIR}/.runtime"
ARCHIVE="${RUNTIME_DIR}/apache-seatunnel-${VERSION}-bin.tar.gz"
SEATUNNEL_HOME="${RUNTIME_DIR}/apache-seatunnel-${VERSION}"
DOWNLOAD_URL="${SEATUNNEL_DOWNLOAD_URL:-}"
MYSQL_DRIVER_VERSION="8.4.0"

mkdir -p "${RUNTIME_DIR}"
if [[ ! -x "${SEATUNNEL_HOME}/bin/seatunnel-cluster.sh" ]]; then
  echo "Downloading Apache SeaTunnel ${VERSION}..."
  urls=(
    "${DOWNLOAD_URL}"
    "https://dlcdn.apache.org/seatunnel/${VERSION}/apache-seatunnel-${VERSION}-bin.tar.gz"
    "https://archive.apache.org/dist/seatunnel/${VERSION}/apache-seatunnel-${VERSION}-bin.tar.gz"
  )
  downloaded=false
  for url in "${urls[@]}"; do
    [[ -z "${url}" ]] && continue
    if curl --fail --location --retry 2 --connect-timeout 20 --continue-at - "${url}" --output "${ARCHIVE}"; then
      downloaded=true
      break
    fi
    echo "Download failed from ${url}; trying the next official endpoint." >&2
  done
  if [[ "${downloaded}" != true ]]; then
    echo "Unable to download SeaTunnel. Set SEATUNNEL_DOWNLOAD_URL to an accessible Apache mirror and retry." >&2
    exit 1
  fi
  tar -xzf "${ARCHIVE}" -C "${RUNTIME_DIR}"
fi

cp "${ROOT_DIR}/scripts/seatunnel-plugin-config" "${SEATUNNEL_HOME}/config/plugin_config"
if ! find "${SEATUNNEL_HOME}/connectors" -maxdepth 1 -name 'connector-jdbc-*.jar' -print -quit | grep -q .; then
  echo "Installing SeaTunnel JDBC connector..."
  (cd "${SEATUNNEL_HOME}" && sh bin/install-plugin.sh "${VERSION}")
fi

MYSQL_DRIVER="${SEATUNNEL_HOME}/lib/mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar"
if [[ ! -f "${MYSQL_DRIVER}" ]]; then
  echo "Downloading MySQL Connector/J ${MYSQL_DRIVER_VERSION}..."
  curl --fail --location --retry 3 \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_DRIVER_VERSION}/mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar" \
    --output "${MYSQL_DRIVER}"
fi

perl -0pi -e 's/(enable-http:\s*true\s*\n\s*port:)\s*8080/$1 8081/' "${SEATUNNEL_HOME}/config/seatunnel.yaml"
perl -0pi -e 's/enable-dynamic-port:\s*true/enable-dynamic-port: false/' "${SEATUNNEL_HOME}/config/seatunnel.yaml"
echo "SeaTunnel is ready at ${SEATUNNEL_HOME}"
