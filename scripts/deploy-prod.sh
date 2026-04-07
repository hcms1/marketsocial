#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${ENV_FILE:-$repo_dir/.env}"
compose_file="$repo_dir/docker-compose.prod.yml"

require_var() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

if [[ ! -f "$env_file" ]]; then
  echo "Missing env file: $env_file" >&2
  echo "Copy .env.prod.example to .env and fill in the production values." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

require_var DOMAIN
require_var DATABASE_USERNAME
require_var DATABASE_PASSWORD

cd "$repo_dir"

mkdir -p uploads postgres-data caddy_data caddy_config

echo "Validating production Docker Compose config..."
docker compose -f "$compose_file" --env-file "$env_file" config >/dev/null

echo "Starting MarketSocial production stack..."
docker compose -f "$compose_file" --env-file "$env_file" up --build -d

echo "Production stack started."
echo "Next checks:"
echo "  1. Visit https://$DOMAIN"
echo "  2. Confirm TLS is valid and the homepage loads"
echo "  3. Run a live smoke test once DNS has propagated"
