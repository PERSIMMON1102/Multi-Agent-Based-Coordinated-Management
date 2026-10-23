#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.server.yml"

"${ROOT_DIR}/deploy/server-preflight.sh"

cd "${ROOT_DIR}"
docker compose --env-file .env -f "${COMPOSE_FILE}" up -d --build
docker compose --env-file .env -f "${COMPOSE_FILE}" ps

echo
echo "Server deployment started."
echo "Frontend upstream: http://127.0.0.1:8081"
echo "Next step: configure host Nginx and HTTPS."
