#!/bin/bash

# Test script to send WhatsApp notification
# Usage: ./test-whatsapp.sh <API_KEY> [PHONE_NUMBER] [MESSAGE]

set -euo pipefail

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

API_KEY=${1:-""}
PHONE_NUMBER=${2:-""}
MESSAGE=${3:-"Hello! This is a test message from the Notification System."}
API_URL=${API_URL:-"http://localhost:8080"}

# Validate API key
if [ -z "$API_KEY" ]; then
    echo -e "${RED}Error: API key is required${NC}"
    echo ""
    echo "Usage: $0 <API_KEY> [PHONE_NUMBER] [MESSAGE]"
    echo ""
    echo "First, register a site to get an API key:"
    echo "  curl -X POST $API_URL/api/v1/site/register \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"siteName\": \"test-site\"}'"
    echo ""
    echo "Then use the apiKey from the response."
    exit 1
fi

# Default phone number if not provided
if [ -z "$PHONE_NUMBER" ]; then
    echo -e "${YELLOW}⚠${NC}  Phone number not provided. Please provide a phone number:"
    echo "  Usage: $0 <API_KEY> <PHONE_NUMBER> [MESSAGE]"
    exit 1
fi

# Convert phone number to WhatsApp JID format if needed
if [[ ! "$PHONE_NUMBER" == *"@s.whatsapp.net" ]]; then
    # Remove + and spaces, add @s.whatsapp.net
    PHONE_NUMBER=$(echo "$PHONE_NUMBER" | sed 's/[+ ]//g')
    PHONE_NUMBER="${PHONE_NUMBER}@s.whatsapp.net"
fi

echo "=========================================="
echo "WhatsApp Notification Test"
echo "=========================================="
echo ""
echo "API URL:     $API_URL"
echo "Recipient:   $PHONE_NUMBER"
echo "API Key:     ${API_KEY:0:20}..."
echo "Message:     $MESSAGE"
echo ""

# Check if API is reachable
if ! curl -s -f "$API_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}✗${NC} API is not reachable at $API_URL"
    echo "Make sure the notification-api service is running"
    exit 1
fi

echo "Sending notification..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/api/v1/notifications/send" \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: $API_KEY" \
  -d "{
    \"channel\": \"WHATSAPP\",
    \"recipient\": \"$PHONE_NUMBER\",
    \"body\": \"$MESSAGE\"
  }")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo ""
if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
    echo -e "${GREEN}✓${NC} Notification sent successfully (HTTP $HTTP_CODE)"
    echo ""
    if command -v jq &> /dev/null; then
        echo "$BODY" | jq '.'
    else
        echo "$BODY"
    fi
else
    echo -e "${RED}✗${NC} Failed to send notification (HTTP $HTTP_CODE)"
    echo ""
    echo "$BODY"
    exit 1
fi

echo ""
echo "=========================================="
echo "Next Steps"
echo "=========================================="
echo ""
echo "Check message status:"
echo "  curl -H 'X-Site-Key: $API_KEY' $API_URL/api/v1/metrics/site/summary"
echo ""
echo "View worker logs:"
echo "  docker compose logs -f whatsapp-worker"
echo ""

