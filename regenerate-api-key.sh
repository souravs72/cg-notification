#!/bin/bash

# Script to regenerate API key for clapgrow.frappe.cloud site
# Usage: ./regenerate-api-key.sh [site-id]

SITE_NAME="clapgrow.frappe.cloud"
ADMIN_URL="http://localhost:8080/admin"

if [ -n "$1" ]; then
    SITE_ID="$1"
    echo "Regenerating API key for site ID: $SITE_ID"
    echo ""
    
    # Call the regenerate endpoint
    RESPONSE=$(curl -s -X POST "$ADMIN_URL/api/sites/$SITE_ID/regenerate-api-key" \
        -H "Content-Type: application/json")
    
    API_KEY=$(echo "$RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('apiKey', 'N/A'))" 2>/dev/null)
    
    if [ "$API_KEY" != "N/A" ] && [ -n "$API_KEY" ]; then
        echo "✓ API key regenerated successfully!"
        echo ""
        echo "⚠️  SAVE THIS API KEY - IT WILL ONLY BE SHOWN ONCE!"
        echo ""
        echo "New API Key: $API_KEY"
        echo ""
        echo "Updating scripts..."
        
        # Update scripts
        sed -i "s|API_KEY=.*|API_KEY=\"$API_KEY\"|" send-bulk-test.sh
        sed -i "s|API_KEY = .*|API_KEY = \"$API_KEY\"|" send-bulk-test.py
        
        echo "✓ Scripts updated!"
        echo ""
        echo "You can now run:"
        echo "  python3 send-bulk-test.py"
    else
        echo "✗ Failed to regenerate API key"
        echo "Response: $RESPONSE"
        exit 1
    fi
else
    echo "=========================================="
    echo "Regenerate API Key for: $SITE_NAME"
    echo "=========================================="
    echo ""
    echo "To regenerate the API key:"
    echo "1. Go to: $ADMIN_URL/sites"
    echo "2. Find the site '$SITE_NAME'"
    echo "3. Note the Site ID (UUID)"
    echo "4. Run: ./regenerate-api-key.sh <site-id>"
    echo ""
    echo "Or use the admin dashboard to regenerate it."
    echo ""
fi



