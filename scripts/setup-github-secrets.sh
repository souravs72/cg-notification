#!/bin/bash

# Script to set up GitHub Secrets for the notification system
# Usage: ./scripts/setup-github-secrets.sh

set -e

REPO="souravs72/cg-notification"

echo "=========================================="
echo "GitHub Secrets Setup"
echo "=========================================="
echo ""
echo "This script will help you set up GitHub Secrets for:"
echo "  - SENDGRID_API_KEY"
echo "  - WASENDER_API_KEY"
echo "  - DB_PASSWORD"
echo "  - DB_USERNAME"
echo ""
echo "Repository: $REPO"
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed"
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Not authenticated with GitHub"
    echo "Run: gh auth login"
    exit 1
fi

echo "✅ GitHub CLI is installed and authenticated"
echo ""

# Function to set secret
set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    if [ -z "$secret_value" ]; then
        echo "⚠️  Skipping $secret_name (no value provided)"
        return
    fi
    
    echo "Setting $secret_name..."
    echo "$secret_value" | gh secret set "$secret_name" --repo "$REPO"
    echo "✅ $secret_name set successfully"
    echo ""
}

# Prompt for secrets
read -sp "Enter SENDGRID_API_KEY (or press Enter to skip): " SENDGRID_KEY
echo ""
set_secret "SENDGRID_API_KEY" "$SENDGRID_KEY"

read -sp "Enter WASENDER_API_KEY (or press Enter to skip): " WASENDER_KEY
echo ""
set_secret "WASENDER_API_KEY" "$WASENDER_KEY"

read -sp "Enter DB_PASSWORD (or press Enter to skip): " DB_PASS
echo ""
set_secret "DB_PASSWORD" "$DB_PASS"

read -p "Enter DB_USERNAME (or press Enter to skip): " DB_USER
echo ""
set_secret "DB_USERNAME" "$DB_USER"

echo "=========================================="
echo "✅ GitHub Secrets setup complete!"
echo "=========================================="
echo ""
echo "You can verify secrets at:"
echo "https://github.com/$REPO/settings/secrets/actions"
echo ""

