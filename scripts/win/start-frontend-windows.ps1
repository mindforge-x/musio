param(
    [switch]$NoWait,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root ".musio\run"
$FrontendDir = Join-Path $Root "frontend"
$FrontendHost = if ($env:MUSIO_WEB_HOST) { $env:MUSIO_WEB_HOST } else { "127.0.0.1" }
$FrontendPort = if ($env:MUSIO_WEB_PORT) { [int]$env:MUSIO_WEB_PORT } else { 18766 }
$BackendBaseUrl = if ($env:MUSIO_BACKEND_BASE_URL) { $env:MUSIO_BACKEND_BASE_URL } else { "http://127.0.0.1:18765" }
$FrontendUrl = "http://$($FrontendHost):$($FrontendPort)/"

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Test-PortListening {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Http {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Host "$Name ready: $Url"
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    Write-Warning "$Name did not become ready within $TimeoutSeconds seconds: $Url"
}

if (-not (Get-Command "npm.cmd" -ErrorAction SilentlyContinue)) {
    throw "npm.cmd was not found in PATH. Install Node.js and ensure npm is available in PATH."
}

$viteCmd = Join-Path $FrontendDir "node_modules\.bin\vite.cmd"
if (-not (Test-Path $viteCmd)) {
    Write-Host "Installing Windows frontend dependencies..."
    Push-Location $FrontendDir
    try {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed."
        }
    } finally {
        Pop-Location
    }
}

if (Test-PortListening -Port $FrontendPort) {
    Write-Host "musio-frontend already appears to be listening on port $FrontendPort"
    if (-not $NoBrowser) {
        Start-Process $FrontendUrl
    }
    exit 0
}

$command = "`$env:VITE_MUSIO_BACKEND_URL='$BackendBaseUrl'; Write-Host 'Starting musio-frontend'; & npm.cmd run dev -- --host $FrontendHost --port $FrontendPort --strictPort"
$process = Start-Process -FilePath "powershell.exe" -WorkingDirectory $FrontendDir -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
Set-Content -Path (Join-Path $RunDir "musio-frontend.pid") -Value $process.Id
Write-Host "musio-frontend launched, pid=$($process.Id)"

if (-not $NoWait) {
    Wait-Http -Name "React frontend" -Url $FrontendUrl -TimeoutSeconds 30
}

if (-not $NoBrowser) {
    Start-Process $FrontendUrl
}
