#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080}"
username="smoke_$(date +%s)"
password="smoke-pass-123"
cookie_file="$(mktemp)"
cleanup() {
  rm -f "$cookie_file"
}
trap cleanup EXIT

register_payload=$(cat <<JSON
{"username":"$username","password":"$password","accountType":"USER"}
JSON
)

register_status=""
for _ in $(seq 1 10); do
  register_status=$(
    curl -sS -o /tmp/marketsocial-register.json -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -d "$register_payload" \
      "$base_url/api/auth/register" || true
  )
  if [[ "$register_status" == "201" ]]; then
    break
  fi
  sleep 2
done

if [[ "$register_status" != "201" ]]; then
  echo "Registration failed with HTTP $register_status"
  cat /tmp/marketsocial-register.json
  exit 1
fi

login_status=$(
  curl -sS -o /tmp/marketsocial-login.txt -w "%{http_code}" \
    -c "$cookie_file" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" \
    "$base_url/login"
)

if [[ "$login_status" != "204" ]]; then
  echo "Login failed with HTTP $login_status"
  cat /tmp/marketsocial-login.txt
  exit 1
fi

me_status=$(
  curl -sS -o /tmp/marketsocial-me.json -w "%{http_code}" \
    -b "$cookie_file" \
    "$base_url/api/auth/me"
)

if [[ "$me_status" != "200" ]]; then
  echo "/api/auth/me failed with HTTP $me_status"
  cat /tmp/marketsocial-me.json
  exit 1
fi

if ! grep -q "\"username\":\"$username\"" /tmp/marketsocial-me.json; then
  echo "Smoke test failed: authenticated user payload did not contain $username"
  cat /tmp/marketsocial-me.json
  exit 1
fi

delete_status=$(
  curl -sS -o /tmp/marketsocial-delete.json -w "%{http_code}" \
    -b "$cookie_file" \
    -H "Content-Type: application/json" \
    -X DELETE \
    -d "{\"password\":\"$password\"}" \
    "$base_url/api/profiles/me"
)

if [[ "$delete_status" != "204" ]]; then
  echo "Smoke test cleanup failed with HTTP $delete_status"
  cat /tmp/marketsocial-delete.json
  exit 1
fi

echo "Smoke test passed for $username"
