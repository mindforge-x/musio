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

## User Configuration

Musio reads user configuration from:

```text
~/.musio/config.toml
```

Copy the example file before starting the backend:

```bash
mkdir -p ~/.musio
cp config/musio.example.toml ~/.musio/config.toml
```

If the backend is launched from WSL through a Windows JDK, Java uses the Windows
home directory instead. For the current development setup that usually means:

```text
C:\Users\<you>\.musio\config.toml
/mnt/c/Users/<you>/.musio/config.toml
```

You can override the path with `MUSIO_CONFIG`.

Example model settings:

```toml
[ai]
provider = "openai-compatible"
base_url = "http://127.0.0.1:11434/v1"
api_key = "${MUSIO_AI_API_KEY:}"
model = "qwen2.5:7b"
temperature = 0.7
max_tokens = 2048

[providers.qqmusic]
sidecar_base_url = "http://127.0.0.1:18767"

[storage]
home = "~/.musio"
```

`api_key` supports environment references in the form `${ENV_NAME}` or
`${ENV_NAME:fallback}`.

The QQ Music sidecar also reads `MUSIO_CONFIG` and `MUSIO_HOME`. By default it
uses `${storage.home}/credentials/qqmusic.json`, so the Spring QR login and the
Python QQMusicApi client share the same credential file. You can override these
sidecar-specific values when debugging:

```bash
MUSIO_QQMUSIC_CREDENTIALS=/path/to/qqmusic.json
MUSIO_QQMUSIC_DEVICE_PATH=/path/to/qqmusic-device.json
MUSIO_QQMUSIC_PROXY=http://127.0.0.1:7890
```

For local Ollama, use an OpenAI-compatible endpoint:

```toml
[ai]
provider = "ollama"
base_url = "http://127.0.0.1:11434/v1"
api_key = ""
model = "qwen2.5:7b"
```

For OpenAI:

```toml
[ai]
provider = "openai"
base_url = "https://api.openai.com"
api_key = "${OPENAI_API_KEY}"
model = "gpt-4.1-mini"
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

The QQ Music QR login flow is implemented in the Spring backend and stores
credentials under the configured `storage.home`. The Spring music API calls the
local Python sidecar, which adapts `qqmusic-api-python` for search, song detail,
play URLs, lyrics, comments, profile, playlists, and playlist songs. Basic chat
now calls the configured OpenAI-compatible model endpoint.
