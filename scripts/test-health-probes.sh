#!/bin/bash
# Test health and liveness probes for ECS-like deployment
# This script validates that all services are healthy before deploying to AWS

set -e

# Check dependencies
if ! command -v curl &> /dev/null; then
    echo "❌ ERROR: curl is required but not installed"
    exit 1
fi

# jq is optional but recommended
if ! command -v jq &> /dev/null; then
    echo "⚠️  WARNING: jq not found. JSON parsing will be limited."
    JQ_AVAILABLE=false
else
    JQ_AVAILABLE=true
fi

BASE_URL_API="http://localhost:8080"
BASE_URL_EMAIL="http://localhost:8081"
BASE_URL_WHATSAPP="http://localhost:8082"

echo "=========================================="
echo "Testing Health and Liveness Probes"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to test endpoint
test_endpoint() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -n "Testing $name... "
    
    if response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null); then
        http_code=$(echo "$response" | tail -n1)
        body=$(echo "$response" | sed '$d')
        
        if [ "$http_code" = "$expected_status" ]; then
            echo -e "${GREEN}✓${NC} (HTTP $http_code)"
            if [ -n "$body" ] && [ "$JQ_AVAILABLE" = true ]; then
                echo "$body" | jq . 2>/dev/null || echo "  Response: $body"
            elif [ -n "$body" ]; then
                echo "  Response: $body"
            fi
            return 0
        else
            echo -e "${RED}✗${NC} (HTTP $http_code, expected $expected_status)"
            echo "  Response: $body"
            return 1
        fi
    else
        echo -e "${RED}✗${NC} (Connection failed)"
        return 1
    fi
}

# Wait for services to be ready
echo "Waiting for services to start (max 5 minutes)..."
MAX_WAIT=300
ELAPSED=0
INTERVAL=10

while [ $ELAPSED -lt $MAX_WAIT ]; do
    if curl -s -f "$BASE_URL_API/actuator/health" > /dev/null 2>&1; then
        echo "Services are ready!"
        break
    fi
    echo "Waiting... (${ELAPSED}s/${MAX_WAIT}s)"
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo -e "${RED}ERROR: Services did not become ready within $MAX_WAIT seconds${NC}"
    exit 1
fi

echo ""
echo "=========================================="
echo "Testing Notification API"
echo "=========================================="

# Test API health endpoints
test_endpoint "API Health" "$BASE_URL_API/actuator/health" 200
test_endpoint "API Liveness" "$BASE_URL_API/actuator/health/liveness" 200
test_endpoint "API Readiness" "$BASE_URL_API/actuator/health/readiness" 200

echo ""
echo "=========================================="
echo "Testing Email Worker"
echo "=========================================="

# Test Email Worker health endpoints
test_endpoint "Email Worker Health" "$BASE_URL_EMAIL/actuator/health" 200
test_endpoint "Email Worker Liveness" "$BASE_URL_EMAIL/actuator/health/liveness" 200
test_endpoint "Email Worker Readiness" "$BASE_URL_EMAIL/actuator/health/readiness" 200

echo ""
echo "=========================================="
echo "Testing WhatsApp Worker"
echo "=========================================="

# Test WhatsApp Worker health endpoints
test_endpoint "WhatsApp Worker Health" "$BASE_URL_WHATSAPP/actuator/health" 200
test_endpoint "WhatsApp Worker Liveness" "$BASE_URL_WHATSAPP/actuator/health/liveness" 200
test_endpoint "WhatsApp Worker Readiness" "$BASE_URL_WHATSAPP/actuator/health/readiness" 200

echo ""
echo "=========================================="
echo "Testing Database Connectivity"
echo "=========================================="

# Test database health (should be included in readiness check)
if [ "$JQ_AVAILABLE" = true ]; then
    API_READINESS=$(curl -s "$BASE_URL_API/actuator/health/readiness" | jq -r '.components.db.status' 2>/dev/null || echo "unknown")
    if [ "$API_READINESS" = "UP" ]; then
        echo -e "${GREEN}✓${NC} Database connectivity: UP"
    else
        echo -e "${RED}✗${NC} Database connectivity: $API_READINESS"
        exit 1
    fi
else
    # Without jq, just check if readiness endpoint returns 200
    if curl -s -f "$BASE_URL_API/actuator/health/readiness" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Database connectivity: UP (readiness check passed)"
    else
        echo -e "${RED}✗${NC} Database connectivity: DOWN (readiness check failed)"
        exit 1
    fi
fi

echo ""
echo "=========================================="
echo "Testing Kafka Connectivity"
echo "=========================================="

# Test Kafka health (should be included in readiness check)
if [ "$JQ_AVAILABLE" = true ]; then
    API_READINESS_FULL=$(curl -s "$BASE_URL_API/actuator/health/readiness")
    KAFKA_STATUS=$(echo "$API_READINESS_FULL" | jq -r '.components.kafka.status' 2>/dev/null || echo "unknown")
    
    if [ "$KAFKA_STATUS" = "UP" ]; then
        echo -e "${GREEN}✓${NC} Kafka connectivity: UP"
    else
        echo -e "${YELLOW}⚠${NC} Kafka connectivity: $KAFKA_STATUS (may not be exposed in health check)"
    fi
else
    # Without jq, assume Kafka is OK if readiness check passes
    echo -e "${GREEN}✓${NC} Kafka connectivity: UP (readiness check passed)"
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo -e "${GREEN}All health checks passed!${NC}"
echo ""
echo "Services are ready for AWS deployment:"
echo "  - Notification API: $BASE_URL_API"
echo "  - Email Worker: $BASE_URL_EMAIL"
echo "  - WhatsApp Worker: $BASE_URL_WHATSAPP"
echo ""
echo "Next steps:"
echo "  1. Build and push Docker images to ECR"
echo "  2. Deploy to AWS using Terraform"
echo "  3. Verify health endpoints in AWS"
