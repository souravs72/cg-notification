#!/bin/bash

# Helper script to get API key for clapgrow.frappe.cloud site
# Note: API keys are only shown once during registration
# If you've lost the key, you need to regenerate it via admin dashboard

SITE_NAME="clapgrow.frappe.cloud"
API_URL="http://localhost:8080/api/v1"

echo "=========================================="
echo "Get API Key for Site: $SITE_NAME"
echo "=========================================="
echo ""

# Try to register (will fail if site exists, but that's OK)
echo "Checking if site exists..."
RESPONSE=$(curl -s -X POST "$API_URL/site/register" \
  -H "Content-Type: application/json" \
  -d "{\"siteName\":\"$SITE_NAME\",\"description\":\"ClapGrow Frappe Cloud Site\"}")

if echo "$RESPONSE" | grep -q "already exists"; then
    echo "✓ Site '$SITE_NAME' already exists"
    echo ""
    echo "⚠️  API keys are only shown once during registration."
    echo ""
    echo "To get or regenerate the API key:"
    echo "1. Go to: http://localhost:8080/admin/sites"
    echo "2. Find the site '$SITE_NAME'"
    echo "3. Click 'Show API Key' or 'Regenerate API Key'"
    echo ""
    echo "Then update the API_KEY in:"
    echo "  - send-bulk-test.sh"
    echo "  - send-bulk-test.py"
else
    # Site was created, extract API key
    API_KEY=$(echo "$RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('apiKey', 'N/A'))" 2>/dev/null)
    
    if [ "$API_KEY" != "N/A" ] && [ -n "$API_KEY" ]; then
        echo "✓ Site registered successfully!"
        echo ""
        echo "⚠️  SAVE THIS API KEY - IT WILL ONLY BE SHOWN ONCE!"
        echo ""
        echo "API Key: $API_KEY"
        echo ""
        echo "Update your scripts with:"
        echo "  export API_KEY=\"$API_KEY\""
        echo ""
        echo "Or update directly in:"
        echo "  - send-bulk-test.sh"
        echo "  - send-bulk-test.py"
    else
        echo "✗ Failed to register site or extract API key"
        echo "Response: $RESPONSE"
    fi
fi

echo ""
echo "=========================================="



