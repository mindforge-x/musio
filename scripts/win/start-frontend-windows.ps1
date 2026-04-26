param(
    [switch]$NoWait,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root ".musio\run"
$FrontendDir = Join-Path $Root "frontend"
$FrontendUrl = "http://127.0.0.1:18766/"

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

if (Test-PortListening -Port 18766) {
    Write-Host "musio-frontend already appears to be listening on port 18766"
    if (-not $NoBrowser) {
        Start-Process $FrontendUrl
    }
    exit 0
}

$command = "Write-Host 'Starting musio-frontend'; & npm.cmd run dev -- --host 127.0.0.1 --port 18766"
$process = Start-Process -FilePath "powershell.exe" -WorkingDirectory $FrontendDir -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
Set-Content -Path (Join-Path $RunDir "musio-frontend.pid") -Value $process.Id
Write-Host "musio-frontend launched, pid=$($process.Id)"

if (-not $NoWait) {
    Wait-Http -Name "React frontend" -Url $FrontendUrl -TimeoutSeconds 30
}

if (-not $NoBrowser) {
    Start-Process $FrontendUrl
}
