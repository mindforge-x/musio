# Musio

Musio is a local-first music agent. The first implementation targets QQ Music,
with Spring Boot as the main backend, a Java CLI as the local launcher, a Python
QQMusicApi sidecar, and a React web console.

## Layout

```text
backend-spring/                  Spring Boot API, agent runtime, auth, memory
cli-java/                        Java launcher and developer CLI
providers/qqmusic-python-sidecar/ QQ Music API HTTP sidecar
frontend/                        React web console
packaging/npm/                   npm/npx launcher shell
scripts/                         Development startup scripts
```

## Ports

```text
Spring backend:          http://127.0.0.1:18765
React web:               http://127.0.0.1:18766
QQMusic Python sidecar:  http://127.0.0.1:18767
```

## Development

```bash
cd providers/qqmusic-python-sidecar
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cd ../..

cd frontend
npm install
cd ..

# One terminal with all services:
./scripts/dev.sh

# Or, for debugging, run these in separate terminals:
./scripts/dev-sidecar.sh
./scripts/dev-backend.sh
./scripts/dev-frontend.sh
```

The scripts expect Java 21, Maven, Python 3.11+, and Node.js. If you are in WSL
with dependencies downloaded through a local proxy, use the proxy flags supported
by each package manager, for example `npm install --proxy=http://127.0.0.1:7890
--https-proxy=http://127.0.0.1:7890`.

If Java is only installed on Windows, `scripts/dev.sh` falls back to
`scripts/mvn-win-jdk21.sh`, which defaults to:

```text
D:\Env\JDK21
D:\Env\Maven\apache-maven-3.6.1-bin\apache-maven-3.6.1
```

Override `MUSIO_JAVA_EXE_WIN` or `MUSIO_MAVEN_HOME_WIN` if those paths change.

## Current Scope

This is the initial project skeleton. The QQ Music QR login flow should be
migrated from the existing `aasee-music-backend` project into:

```text
backend-spring/src/main/java/com/musio/providers/qqmusic/QQMusicAuthService.java
backend-spring/src/main/java/com/musio/providers/qqmusic/QQMusicCredentialStore.java
```

Agent execution is intentionally routed through backend interfaces so the React
frontend and provider sidecars do not depend on each other directly.
