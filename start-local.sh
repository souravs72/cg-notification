#!/bin/bash

# Start services locally (for development)
# Usage: ./start-local.sh [service]
#   service: api | email-worker | whatsapp-worker | all
#   If not specified, starts infrastructure only

set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SERVICE=${1:-infra}

# Check prerequisites
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗${NC} Maven is not installed"
    exit 1
fi

if ! command -v docker &> /dev/null || ! docker info &> /dev/null; then
    echo -e "${RED}✗${NC} Docker is not installed or not running"
    exit 1
fi

# Start infrastructure
echo "Starting infrastructure services..."
if docker compose up -d postgres zookeeper kafka; then
    echo -e "${GREEN}✓${NC} Infrastructure services started"
    echo "Waiting for services to be ready..."
    sleep 15
else
    echo -e "${RED}✗${NC} Failed to start infrastructure"
    exit 1
fi

# Build common-proto if needed
if [ ! -f "common-proto/target/common-proto-1.0.0.jar" ]; then
    echo "Building common-proto..."
    cd common-proto
    mvn clean install -DskipTests -q
    cd ..
    echo -e "${GREEN}✓${NC} common-proto built"
fi

# Start requested service
case "$SERVICE" in
    api)
        echo ""
        echo "Starting Notification API..."
        cd notification-api
        mvn spring-boot:run -Dspring-boot.run.profiles=local
        ;;
    email-worker)
        echo ""
        echo "Starting Email Worker..."
        cd email-worker
        mvn spring-boot:run -Dspring-boot.run.profiles=local
        ;;
    whatsapp-worker)
        echo ""
        echo "Starting WhatsApp Worker..."
        cd whatsapp-worker
        mvn spring-boot:run -Dspring-boot.run.profiles=local
        ;;
    all)
        echo ""
        echo -e "${YELLOW}⚠${NC}  To run all services, use separate terminals:"
        echo ""
        echo -e "${GREEN}Terminal 1 - API:${NC}"
        echo "  ./start-local.sh api"
        echo ""
        echo -e "${GREEN}Terminal 2 - Email Worker:${NC}"
        echo "  ./start-local.sh email-worker"
        echo ""
        echo -e "${GREEN}Terminal 3 - WhatsApp Worker:${NC}"
        echo "  ./start-local.sh whatsapp-worker"
        echo ""
        echo "Or use Maven directly:"
        echo "  cd notification-api && mvn spring-boot:run -Dspring-boot.run.profiles=local"
        ;;
    infra)
        echo ""
        echo -e "${GREEN}✓${NC} Infrastructure is ready!"
        echo ""
        echo "To start services, run in separate terminals:"
        echo "  ./start-local.sh api"
        echo "  ./start-local.sh email-worker"
        echo "  ./start-local.sh whatsapp-worker"
        ;;
    *)
        echo -e "${RED}✗${NC} Unknown service: $SERVICE"
        echo "Usage: $0 [api|email-worker|whatsapp-worker|all|infra]"
        exit 1
        ;;
esac
