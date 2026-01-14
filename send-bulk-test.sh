#!/bin/bash

# Bulk notification test script
# Sends messages to multiple phone numbers and email addresses
# Site: clapgrow.frappe.cloud

API_URL="http://localhost:8080/api/v1"
# API Key for clapgrow.frappe.cloud site
API_KEY="Q8S7MnPJSHZlx2Sfz9NqV1mkKCWY188auuVEfyASoh8yCQYMwntUnt3j3Ud7nNErq_XULuYqDGKdMMTVBCy83g"

# Phone numbers
PHONE_NUMBERS=(
  "+918576882906"
  "+918910261784"
  "+917044345461"
  "+919836858910"
  "+917003999234"
  "+918335982196"
  "+919804969862"
)

# Email addresses
EMAIL_ADDRESSES=(
  "sourav@clapgrow.com"
  "souravns1997@gmail.com"
  "souravsingh2609@gmail.com"
  "singh.sourav2609@gmail.com"
)

# Message content
EMAIL_SUBJECT="Test Bulk Notification"
EMAIL_BODY="Hello! This is a test bulk email notification. Testing the efficiency of the notification system."
WHATSAPP_BODY="Hello! This is a test bulk WhatsApp notification. Testing the efficiency of the notification system."

echo "=========================================="
echo "Bulk Notification Test"
echo "=========================================="
echo ""
echo "Sending to:"
echo "  - ${#PHONE_NUMBERS[@]} phone numbers"
echo "  - ${#EMAIL_ADDRESSES[@]} email addresses"
echo "  - Total: $((${#PHONE_NUMBERS[@]} + ${#EMAIL_ADDRESSES[@]})) notifications"
echo ""

# Build the bulk notification JSON
BULK_JSON='{"notifications":['

# Add WhatsApp notifications
for phone in "${PHONE_NUMBERS[@]}"; do
  BULK_JSON+='{"channel":"WHATSAPP","recipient":"'$phone'","body":"'$WHATSAPP_BODY'"},'
done

# Add Email notifications
for email in "${EMAIL_ADDRESSES[@]}"; do
  BULK_JSON+='{"channel":"EMAIL","recipient":"'$email'","subject":"'$EMAIL_SUBJECT'","body":"'$EMAIL_BODY'","isHtml":false},'
done

# Remove trailing comma and close JSON
BULK_JSON=${BULK_JSON%,}
BULK_JSON+=']}'

echo "Sending bulk notifications..."
echo ""

# Send bulk request
RESPONSE=$(curl -s -X POST "$API_URL/notifications/send/bulk" \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: $API_KEY" \
  -d "$BULK_JSON")

# Check if curl was successful
if [ $? -eq 0 ]; then
  echo "Response:"
  echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
  echo ""
  
  # Extract message IDs if available
  TOTAL_QUEUED=$(echo "$RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('totalQueued', 'N/A'))" 2>/dev/null)
  
  if [ "$TOTAL_QUEUED" != "N/A" ]; then
    echo "✓ Successfully queued $TOTAL_QUEUED notifications"
    echo ""
    echo "Waiting 5 seconds before checking status..."
    sleep 5
    
    echo ""
    echo "Checking message logs..."
    LOGS=$(curl -s -H "X-Site-Key: $API_KEY" "$API_URL/messages/logs?size=20")
    echo "$LOGS" | python3 -m json.tool 2>/dev/null || echo "$LOGS"
  fi
else
  echo "✗ Failed to send bulk notifications"
  exit 1
fi

echo ""
echo "=========================================="
echo "Test completed!"
echo "=========================================="
echo ""
echo "You can check the status of messages at:"
echo "  curl -H \"X-Site-Key: $API_KEY\" $API_URL/messages/logs"
echo ""
echo "Or view metrics at:"
echo "  curl -H \"X-Site-Key: $API_KEY\" $API_URL/metrics/site/summary"



