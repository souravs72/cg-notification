#!/bin/bash

# Set up GitHub Secrets for CI/CD
# Usage: ./scripts/setup-github-secrets.sh

set -euo pipefail

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Repository (update if different)
REPO=${GITHUB_REPO:-"souravs72/cg-notification"}

echo "=========================================="
echo "GitHub Secrets Setup"
echo "=========================================="
echo ""
echo "This script will help you set up GitHub Secrets for CI/CD:"
echo "  - SENDGRID_API_KEY"
echo "  - WASENDER_API_KEY"
echo "  - DB_PASSWORD"
echo "  - DB_USERNAME"
echo ""
echo "Repository: $REPO"
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}✗${NC} GitHub CLI (gh) is not installed"
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}✗${NC} Not authenticated with GitHub"
    echo "Run: gh auth login"
    exit 1
fi

echo -e "${GREEN}✓${NC} GitHub CLI is installed and authenticated"
echo ""

# Function to set secret
set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    if [ -z "$secret_value" ]; then
        echo -e "${YELLOW}⚠${NC}  Skipping $secret_name (no value provided)"
        return 0
    fi
    
    echo "Setting $secret_name..."
    if echo "$secret_value" | gh secret set "$secret_name" --repo "$REPO" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $secret_name set successfully"
    else
        echo -e "${RED}✗${NC} Failed to set $secret_name"
        return 1
    fi
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
echo -e "${GREEN}✓ GitHub Secrets setup complete!${NC}"
echo "=========================================="
echo ""
echo "You can verify secrets at:"
echo "https://github.com/$REPO/settings/secrets/actions"
echo ""

