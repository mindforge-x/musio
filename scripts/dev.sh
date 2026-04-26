#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup() {
  jobs -p | xargs -r kill
}
trap cleanup EXIT

cd "$ROOT_DIR/providers/qqmusic-python-sidecar"
if [ -x ".venv/bin/python" ]; then
  .venv/bin/python -m app.main &
else
  python3 -m app.main &
fi

if command -v java >/dev/null 2>&1 && command -v mvn >/dev/null 2>&1; then
  cd "$ROOT_DIR/backend-spring"
  mvn spring-boot:run &
else
  cd "$ROOT_DIR"
  "$ROOT_DIR/scripts/mvn-win-jdk21.sh" -pl backend-spring spring-boot:run &
fi

cd "$ROOT_DIR/frontend"
npm run dev -- --host 127.0.0.1 --port 18766 &

wait
