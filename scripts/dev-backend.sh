#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v java >/dev/null 2>&1 && command -v mvn >/dev/null 2>&1; then
  cd "$ROOT_DIR/backend-spring"
  exec mvn spring-boot:run
fi

cd "$ROOT_DIR"
exec "$ROOT_DIR/scripts/mvn-win-jdk21.sh" -pl backend-spring spring-boot:run
