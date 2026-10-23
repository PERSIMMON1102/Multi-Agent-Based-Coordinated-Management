#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.server.yml"

"${ROOT_DIR}/deploy/server-preflight.sh"

cd "${ROOT_DIR}"
docker compose --env-file .env -f "${COMPOSE_FILE}" pull || true
docker compose --env-file .env -f "${COMPOSE_FILE}" up -d --build
docker compose --env-file .env -f "${COMPOSE_FILE}" ps

echo
echo "Server update finished."
echo "Check logs if needed:"
echo "  docker compose --env-file .env -f ${COMPOSE_FILE} logs -f cdm-backend"
echo "  docker compose --env-file .env -f ${COMPOSE_FILE} logs -f cdm-frontend"
