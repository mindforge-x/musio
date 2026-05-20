#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/frontend"

HOST="${MUSIO_WEB_HOST:-127.0.0.1}"
PORT="${MUSIO_WEB_PORT:-18766}"
BACKEND_BASE_URL="${MUSIO_BACKEND_BASE_URL:-http://127.0.0.1:18765}"

dependencies_current() {
  local vite_bin="node_modules/.bin/vite"
  local package_lock="package-lock.json"
  local installed_lock="node_modules/.package-lock.json"

  [[ -x "$vite_bin" ]] || return 1
  [[ -f "$package_lock" ]] || return 0
  [[ -f "$installed_lock" ]] || return 1
  [[ ! "$package_lock" -nt "$installed_lock" ]]
}

port_listening() {
  if command -v curl >/dev/null 2>&1 && curl --noproxy '*' -fsS --max-time 2 "http://$HOST:$PORT/" >/dev/null 2>&1; then
    return 0
  fi
  if command -v nc >/dev/null 2>&1 && nc -z "$HOST" "$PORT" >/dev/null 2>&1; then
    return 0
  fi
  local port_hex
  port_hex="$(printf '%04X' "$PORT")"
  if awk -v port="$port_hex" '
    NR > 1 {
      split($2, local_addr, ":")
      if (local_addr[2] == port && $4 == "0A") {
        found = 1
      }
    }
    END { exit found ? 0 : 1 }
  ' /proc/net/tcp /proc/net/tcp6 2>/dev/null; then
    return 0
  fi
  return 1
}

if ! dependencies_current; then
  echo "Installing frontend dependencies..."
  npm install
fi

if port_listening; then
  echo "musio-frontend already appears to be listening on port $PORT"
  exit 0
fi

VITE_MUSIO_BACKEND_URL="$BACKEND_BASE_URL" exec npm run dev -- --host "$HOST" --port "$PORT" --strictPort
