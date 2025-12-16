#!/bin/bash

# Verify local development setup
# Checks prerequisites, builds project, and verifies infrastructure

set -euo pipefail

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ERRORS=0

echo "=========================================="
echo "Local Development Setup Verification"
echo "=========================================="
echo ""

# Check command availability
check_command() {
    local cmd=$1
    local name=$2
    local required=${3:-true}
    
    if command -v "$cmd" &> /dev/null; then
        echo -e "${GREEN}✓${NC} $name is installed"
        return 0
    else
        if [ "$required" = true ]; then
            echo -e "${RED}✗${NC} $name is not installed"
            ((ERRORS++))
            return 1
        else
            echo -e "${YELLOW}⚠${NC}  $name is not installed (optional)"
            return 0
        fi
    fi
}

# Check Java
echo "Checking prerequisites..."
check_command java "Java 17+"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    echo "  Version: $JAVA_VERSION"
fi

check_command mvn "Maven 3.9+"
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1 | awk '{print $3}')
    echo "  Version: $MVN_VERSION"
fi

check_command docker "Docker"
if command -v docker &> /dev/null; then
    if docker info &> /dev/null; then
        echo -e "${GREEN}✓${NC} Docker daemon is running"
    else
        echo -e "${RED}✗${NC} Docker daemon is not running"
        ((ERRORS++))
    fi
    
    if docker compose version &> /dev/null; then
        echo -e "${GREEN}✓${NC} Docker Compose is available"
    else
        echo -e "${RED}✗${NC} Docker Compose is not available"
        ((ERRORS++))
    fi
fi

check_command jq "jq" false

echo ""

# Build project
if [ $ERRORS -eq 0 ]; then
    echo "Building project..."
    if mvn clean install -DskipTests -q; then
        echo -e "${GREEN}✓${NC} Project builds successfully"
    else
        echo -e "${RED}✗${NC} Project build failed"
        ((ERRORS++))
    fi
else
    echo -e "${YELLOW}⚠${NC}  Skipping build due to missing prerequisites"
fi

echo ""

# Check infrastructure
echo "Checking infrastructure services..."
check_service() {
    local service=$1
    local container=$2
    
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        local status=$(docker ps --filter "name=${container}" --format '{{.Status}}')
        echo -e "${GREEN}✓${NC} $service is running ($status)"
        return 0
    else
        echo -e "${YELLOW}⚠${NC}  $service is not running"
        echo "  Start with: docker compose up -d $service"
        return 1
    fi
}

POSTGRES_UP=false
KAFKA_UP=false

if check_service "PostgreSQL" "notification-postgres"; then
    POSTGRES_UP=true
    # Test database connection
    if docker exec notification-postgres pg_isready -U notification_user -d notification_db &> /dev/null; then
        echo -e "${GREEN}✓${NC} Database connection successful"
    else
        echo -e "${YELLOW}⚠${NC}  Database connection test failed"
    fi
fi

check_service "Zookeeper" "notification-zookeeper"

if check_service "Kafka" "notification-kafka"; then
    KAFKA_UP=true
    # Test Kafka connection
    if docker exec notification-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &> /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Kafka connection successful"
    else
        echo -e "${YELLOW}⚠${NC}  Kafka connection test failed (may need more time to start)"
    fi
fi

echo ""
echo "=========================================="
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✓ Verification Complete!${NC}"
else
    echo -e "${YELLOW}⚠${NC}  Verification completed with $ERRORS error(s)"
fi
echo "=========================================="
echo ""

if [ "$POSTGRES_UP" = false ] || [ "$KAFKA_UP" = false ]; then
    echo "To start infrastructure:"
    echo "  docker compose up -d postgres zookeeper kafka"
    echo ""
fi

echo "To run services locally:"
echo "  ./start-local.sh api           # Start API"
echo "  ./start-local.sh email-worker  # Start Email Worker"
echo "  ./start-local.sh whatsapp-worker # Start WhatsApp Worker"
echo ""

