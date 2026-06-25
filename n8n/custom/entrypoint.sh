#!/bin/sh
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== TalentPredict n8n + PDF Server Entrypoint ==="

# Function to wait for a service
wait_for_service() {
  url=$1
  max_attempts=30
  attempt=0

  echo "Waiting for $url..."
  while [ $attempt -lt $max_attempts ]; do
    if node -e "require('http').get('$url', (res) => process.exit(res.statusCode === 200 ? 0 : 1)).on('error', () => process.exit(1))" >/dev/null 2>&1; then
      echo "✓ Service available at $url"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done

  echo "⚠ Service at $url may not be ready yet (continuing anyway)"
  return 1
}

# Start PDF server in background
echo "Starting PDF Extraction Server..."
cd /home/node/pdf-server
node pdf-server.js &
PDF_SERVER_PID=$!
echo "✓ PDF Server started (PID: $PDF_SERVER_PID)"

# Wait a moment for PDF server to be ready
sleep 3
if wait_for_service "http://localhost:3001/health"; then
  echo "✓ PDF Server is responding"
else
  echo "⚠ PDF Server may still be starting"
fi

# Start n8n
echo "Starting n8n Server..."
cd /home/node

# Pass all arguments to n8n
exec /docker-entrypoint.sh "$@"
