#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.server.yml}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is not installed or not in PATH."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: docker compose plugin is not available."
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "ERROR: ${COMPOSE_FILE} is missing."
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: ${ENV_FILE} is missing."
  echo "Run: cp deploy/env.production.example .env"
  exit 1
fi

required_keys=(
  INITIAL_ADMIN_USERNAME
  INITIAL_ADMIN_PASSWORD
  MYSQL_ROOT_PASSWORD
)

placeholder_values=(
  ChangeMeToAStrongPassword123
  ChangeMySqlRootPassword123
  replace-with-your-llm-api-key
)

missing_keys=()
placeholder_hits=()

for key in "${required_keys[@]}"; do
  if ! grep -Eq "^${key}=.+" "${ENV_FILE}"; then
    missing_keys+=("${key}")
  fi
done

for value in "${placeholder_values[@]}"; do
  if grep -Fq "${value}" "${ENV_FILE}"; then
    placeholder_hits+=("${value}")
  fi
done

if (( ${#missing_keys[@]} > 0 )); then
  echo "ERROR: missing required values in .env: ${missing_keys[*]}"
  exit 1
fi

if (( ${#placeholder_hits[@]} > 0 )); then
  echo "ERROR: .env still contains placeholder values: ${placeholder_hits[*]}"
  exit 1
fi

if ! grep -Eq '^LLM_API_KEY=.+' "${ENV_FILE}" && ! grep -Eq '^LLM_API_BASE_URL=.+' "${ENV_FILE}"; then
  echo "WARNING: LLM configuration looks incomplete. AI-related features may fail."
fi

echo "Preflight passed."
echo "Compose file: ${COMPOSE_FILE}"
echo "Env file: ${ENV_FILE}"
