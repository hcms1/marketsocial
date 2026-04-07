#!/usr/bin/env bash
set -euo pipefail

repo_dir="/home/harrison/Desktop/clearnet/java/marketsocial"
run_smoke=1
base_url="${BASE_URL:-http://localhost:8080}"

for arg in "$@"; do
  case "$arg" in
    --no-smoke)
      run_smoke=0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      echo "Usage: $(basename "$0") [--no-smoke]" >&2
      exit 1
      ;;
  esac
done

cd "$repo_dir"

echo "Restarting MarketSocial Docker stack..."
docker compose down
docker compose up --build -d

if [[ "$run_smoke" -eq 1 ]]; then
  echo "Waiting for MarketSocial to accept HTTP..."
  for _ in $(seq 1 60); do
    if curl -fsS -o /dev/null "$base_url/"; then
      break
    fi
    sleep 2
  done

  echo "Running smoke test..."
  BASE_URL="$base_url" ./scripts/docker-smoke.sh
fi

echo "MarketSocial is up."
