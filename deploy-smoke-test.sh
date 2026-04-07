#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  MarketSocial Post-Deploy Smoke Tests"
echo "=========================================="
echo ""

APP_URL="http://localhost:8080"
PASS=0
FAIL=0

test_endpoint() {
    local name="$1"
    local url="$2"
    local expected_status="$3"
    
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    
    if [[ "$status" == "$expected_status" ]]; then
        echo "✅ $name: $status (expected $expected_status)"
        ((PASS++))
    else
        echo "❌ $name: $status (expected $expected_status)"
        ((FAIL++))
    fi
}

test_content() {
    local name="$1"
    local url="$2"
    local pattern="$3"
    
    local content
    content=$(curl -s "$url" 2>/dev/null || echo "")
    
    if echo "$content" | grep -q "$pattern"; then
        echo "✅ $name: Found pattern"
        ((PASS++))
    else
        echo "❌ $name: Pattern not found"
        ((FAIL++))
    fi
}

echo "Testing homepage..."
test_endpoint "Homepage" "$APP_URL/" "200"
test_content "Homepage has title" "$APP_URL/" "MarketSocial"

echo ""
echo "Testing API endpoints (should require auth)..."
test_endpoint "Orders endpoint" "$APP_URL/api/orders/mine" "401"
test_endpoint "Products endpoint" "$APP_URL/api/products" "401"
test_endpoint "Posts endpoint" "$APP_URL/api/posts" "401"
test_endpoint "Messages endpoint" "$APP_URL/api/messages" "401"

echo ""
echo "Testing app health..."
HEALTH=$(curl -s "$APP_URL/" 2>/dev/null || echo "")
if echo "$HEALTH" | grep -q "app-panel"; then
    echo "✅ App panel present in HTML"
    ((PASS++))
else
    echo "❌ App panel not found in HTML"
    ((FAIL++))
fi

echo ""
echo "Testing Docker containers..."
if docker compose -f /home/ubuntu/marketsocial/docker-compose.yml ps -q app | grep -q .; then
    echo "✅ App container is running"
    ((PASS++))
else
    echo "❌ App container not found"
    ((FAIL++))
fi

if docker compose -f /home/ubuntu/marketsocial/docker-compose.yml ps -q db | grep -q .; then
    echo "✅ DB container is running"
    ((PASS++))
else
    echo "❌ DB container not found"
    ((FAIL++))
fi

echo ""
echo "=========================================="
echo "  Results: $PASS passed, $FAIL failed"
echo "=========================================="

if [[ $FAIL -gt 0 ]]; then
    exit 1
fi

echo ""
echo "✅ All smoke tests passed!"
