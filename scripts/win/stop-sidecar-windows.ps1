$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$StopScript = Join-Path $PSScriptRoot "stop-windows.ps1"
$PidFile = Join-Path $Root ".musio\run\musio-sidecar.pid"

& $StopScript -PidFile $PidFile -Ports @(18767) -Pattern "app\.main"
