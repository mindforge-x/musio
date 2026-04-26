$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\providers\qqmusic-python-sidecar'; python -m app.main"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\backend-spring'; mvn spring-boot:run"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\frontend'; npm run dev -- --host 127.0.0.1 --port 18766"
