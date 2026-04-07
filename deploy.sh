#!/usr/bin/env bash
set -euo pipefail

echo "=== deploy start ==="

cd /home/ubuntu/marketsocial

echo "Fetching latest changes..."
git fetch origin main

echo "Checking out main..."
git checkout main
git reset --hard origin/main

echo "Building and starting containers..."
docker compose build --no-cache app
docker compose up -d

echo "Waiting for app to be healthy..."
sleep 10

echo "Running smoke test..."
if curl -sf http://localhost:8080/ >/dev/null; then
    echo "App is healthy and responding!"
else
    echo "Warning: App may not be responding yet, but deploy completed."
fi

echo "=== deploy done ==="
