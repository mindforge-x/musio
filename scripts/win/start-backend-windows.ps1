param(
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root ".musio\run"
$BackendDir = Join-Path $Root "backend-spring"
$BackendUrl = "http://127.0.0.1:18765/actuator/health"

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Test-PortListening {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Http {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 90)
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

function Assert-Command {
    param([string]$Command, [string]$InstallHint)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "$Command was not found in PATH. $InstallHint"
    }
}

function Assert-Java21 {
    Assert-Command -Command "java" -InstallHint "Install JDK 21 and ensure java is available in PATH."
    $versionOutput = (& cmd /c "java -version 2>&1") -join "`n"
    if ($versionOutput -notmatch 'version "(\d+)') {
        throw "Could not detect Java version. Output: $versionOutput"
    }
    if ([int]$Matches[1] -lt 21) {
        throw "Java 21+ is required. Current java -version output: $versionOutput"
    }
}

Assert-Java21
Assert-Command -Command "mvn" -InstallHint "Install Maven and ensure mvn is available in PATH."

if (Test-PortListening -Port 18765) {
    Write-Host "musio-backend already appears to be listening on port 18765"
    exit 0
}

$command = "Write-Host 'Starting musio-backend'; & mvn spring-boot:run"
$process = Start-Process -FilePath "powershell.exe" -WorkingDirectory $BackendDir -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
Set-Content -Path (Join-Path $RunDir "musio-backend.pid") -Value $process.Id
Write-Host "musio-backend launched, pid=$($process.Id)"

if (-not $NoWait) {
    Wait-Http -Name "Spring backend" -Url $BackendUrl -TimeoutSeconds 90
}
