#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.server.yml"

cd "${ROOT_DIR}"
docker compose --env-file .env -f "${COMPOSE_FILE}" down

echo "Server services stopped."
