#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup() {
  jobs -p | xargs -r kill
}
trap cleanup EXIT

"$ROOT_DIR/scripts/dev-sidecar.sh" &
"$ROOT_DIR/scripts/dev-backend.sh" &
"$ROOT_DIR/scripts/dev-frontend.sh" &

wait
