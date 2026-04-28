#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/frontend"

HOST="${MUSIO_WEB_HOST:-127.0.0.1}"
PORT="${MUSIO_WEB_PORT:-18766}"
BACKEND_BASE_URL="${MUSIO_BACKEND_BASE_URL:-http://127.0.0.1:18765}"

VITE_MUSIO_BACKEND_URL="$BACKEND_BASE_URL" exec npm run dev -- --host "$HOST" --port "$PORT" --strictPort
