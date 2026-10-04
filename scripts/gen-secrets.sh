#!/usr/bin/env bash
# Generate cryptographically secure random secrets for .env configuration

set -euo pipefail

if command -v openssl >/dev/null 2>&1; then
    JWT_SECRET=$(openssl rand -base64 48 | tr -dc 'a-zA-Z0-9' | head -c 64)
    MONGO_PASSWORD=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 32)
else
    # Fallback to /dev/urandom
    JWT_SECRET=$(head -c 48 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 64)
    MONGO_PASSWORD=$(head -c 24 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 32)
fi

echo "=========================================================="
echo "Generated Cryptographically Secure Random Secrets for .env"
echo "=========================================================="
echo "JWT_SECRET=${JWT_SECRET}"
echo "MONGO_INITDB_ROOT_PASSWORD=${MONGO_PASSWORD}"
echo "=========================================================="

