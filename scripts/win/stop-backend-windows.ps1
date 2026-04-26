$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$StopScript = Join-Path $PSScriptRoot "stop-windows.ps1"
$PidFile = Join-Path $Root ".musio\run\musio-backend.pid"

& $StopScript -PidFile $PidFile -Ports @(18765) -Pattern "spring-boot:run|mvn"
