#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIN_ROOT="$(wslpath -w "$ROOT_DIR")"

MUSIO_JAVA_EXE_WIN="${MUSIO_JAVA_EXE_WIN:-D:\\Env\\JDK21\\bin\\java.exe}"
CLI_JAR_WIN="${WIN_ROOT}\\cli-java\\target\\musio-cli.jar"
CLI_JAR_WSL="$ROOT_DIR/cli-java/target/musio-cli.jar"

if [ ! -f "$CLI_JAR_WSL" ]; then
  echo "Musio CLI jar was not found: $CLI_JAR_WSL"
  echo "Build it first:"
  echo "  ./scripts/mvn-win-jdk21.sh -pl cli-java -am package"
  exit 1
fi

CMD="\"${MUSIO_JAVA_EXE_WIN}\" -jar \"${CLI_JAR_WIN}\""
for arg in "$@"; do
  escaped="${arg//\"/\\\"}"
  CMD="${CMD} \"${escaped}\""
done

cmd.exe /C "$CMD"
