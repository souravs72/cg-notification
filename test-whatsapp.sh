#!/bin/bash

# Test script to send WhatsApp notification
# Usage: ./test-whatsapp.sh <API_KEY> <PHONE_NUMBER>

API_KEY=${1:-""}
PHONE_NUMBER=${2:-"918576882906@s.whatsapp.net"}

if [ -z "$API_KEY" ]; then
    echo "Usage: $0 <API_KEY> [PHONE_NUMBER]"
    echo ""
    echo "First, register a site to get an API key:"
    echo "  curl -X POST http://localhost:8080/api/v1/site/register \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"siteName\": \"test-site\"}'"
    echo ""
    echo "Then use the apiKey from the response."
    exit 1
fi

# Convert phone number to WhatsApp JID format if needed
if [[ ! "$PHONE_NUMBER" == *"@s.whatsapp.net" ]]; then
    # Remove + and spaces, add @s.whatsapp.net
    PHONE_NUMBER=$(echo "$PHONE_NUMBER" | sed 's/[+ ]//g')
    PHONE_NUMBER="${PHONE_NUMBER}@s.whatsapp.net"
fi

echo "Sending WhatsApp message to: $PHONE_NUMBER"
echo "Using API Key: ${API_KEY:0:20}..."
echo ""

curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: $API_KEY" \
  -d "{
    \"channel\": \"WHATSAPP\",
    \"recipient\": \"$PHONE_NUMBER\",
    \"body\": \"Hello! This is a test message from the Notification System.\"
  }" | jq '.' 2>/dev/null || cat

echo ""
echo ""
echo "Check the response above. If successful, check:"
echo "  - WhatsApp worker logs"
echo "  - Message status: curl -H 'X-Site-Key: $API_KEY' http://localhost:8080/api/v1/metrics/site/summary"

