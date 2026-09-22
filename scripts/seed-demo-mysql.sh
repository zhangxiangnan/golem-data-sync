#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${ROOT_DIR}/.env" ]]; then set -a; source "${ROOT_DIR}/.env"; set +a; fi
HOST="${DEMO_MYSQL_HOST:-127.0.0.1}"
PORT="${DEMO_MYSQL_PORT:-3306}"
ADMIN_USER="${DEMO_MYSQL_USER:-root}"
SYNC_USER="${DEMO_SYNC_USER:-golem_sync}"
SYNC_PASSWORD="${DEMO_SYNC_PASSWORD:-golem_sync_dev}"
export MYSQL_PWD="${DEMO_MYSQL_PASSWORD:-}"

mysql --host="${HOST}" --port="${PORT}" --user="${ADMIN_USER}" --protocol=tcp <<SQL
CREATE DATABASE IF NOT EXISTS golem_sync_source CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS golem_sync_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${SYNC_USER}'@'%' IDENTIFIED BY '${SYNC_PASSWORD}';
GRANT SELECT ON golem_sync_source.* TO '${SYNC_USER}'@'%';
GRANT ALL PRIVILEGES ON golem_sync_target.* TO '${SYNC_USER}'@'%';
FLUSH PRIVILEGES;
CREATE TABLE IF NOT EXISTS golem_sync_source.orders (
  id BIGINT PRIMARY KEY,
  customer_name VARCHAR(100) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
INSERT INTO golem_sync_source.orders (id, customer_name, amount, status, created_at) VALUES
  (1001, 'Ada', 120.50, 'PAID', NOW()),
  (1002, 'Linus', 89.00, 'PAID', NOW()),
  (1003, 'Grace', 236.80, 'REFUNDED', NOW())
ON DUPLICATE KEY UPDATE customer_name = VALUES(customer_name), amount = VALUES(amount), status = VALUES(status);
SQL
unset MYSQL_PWD
echo "Demo databases are ready. Use ${SYNC_USER} / ${SYNC_PASSWORD} in the Data Sources page."
