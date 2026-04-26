#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/providers/qqmusic-python-sidecar"

if [ -x ".venv/bin/python" ]; then
  exec .venv/bin/python -m app.main
fi

exec python3 -m app.main
