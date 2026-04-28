param(
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root ".musio\run"
$SidecarDir = Join-Path $Root "providers\qqmusic-python-sidecar"
$SidecarHost = if ($env:MUSIO_QQMUSIC_HOST) { $env:MUSIO_QQMUSIC_HOST } else { "127.0.0.1" }
$SidecarPort = if ($env:MUSIO_QQMUSIC_PORT) { [int]$env:MUSIO_QQMUSIC_PORT } else { 18767 }
$SidecarUrl = "http://$($SidecarHost):$($SidecarPort)/health"

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

function Resolve-Python311 {
    $candidates = @()
    if ($env:MUSIO_PYTHON_EXE) {
        $candidates += @{ Exe = $env:MUSIO_PYTHON_EXE; Args = @() }
    }
    $candidates += @(
        @{ Exe = "py"; Args = @("-3.11") },
        @{ Exe = "python"; Args = @() },
        @{ Exe = "python3"; Args = @() }
    )

    foreach ($candidate in $candidates) {
        try {
            $args = @($candidate.Args) + @("-c", "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)")
            & $candidate.Exe @args *> $null
            if ($LASTEXITCODE -eq 0) {
                return [pscustomobject]$candidate
            }
        } catch {
        }
    }
    return $null
}

function Prepare-SidecarPython {
    $venvDir = Join-Path $SidecarDir ".venv-win"
    $pythonExe = Join-Path $venvDir "Scripts\python.exe"
    $requirements = Join-Path $SidecarDir "requirements.txt"

    if (-not (Test-Path $pythonExe)) {
        $python = Resolve-Python311
        if ($null -eq $python) {
            throw "Python 3.11+ was not found. Install Python 3.11+ for Windows or set MUSIO_PYTHON_EXE to its python.exe path."
        }

        Write-Host "Creating Windows Python venv: $venvDir"
        $venvArgs = @($python.Args) + @("-m", "venv", $venvDir)
        $venvOutput = & $python.Exe @venvArgs 2>&1
        $venvExitCode = $LASTEXITCODE
        $venvOutput | ForEach-Object { Write-Host $_ }
        if ($venvExitCode -ne 0) {
            throw "Failed to create Python venv at $venvDir"
        }
    }

    & $pythonExe -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
    if ($LASTEXITCODE -ne 0) {
        throw "Sidecar venv must use Python 3.11+. Delete $venvDir and rerun this script."
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $pythonExe -c "import fastapi, uvicorn, qqmusic_api" *> $null
        $dependenciesReady = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if (-not $dependenciesReady) {
        Write-Host "Installing Python sidecar dependencies..."
        $pipOutput = & $pythonExe -m pip install -r $requirements 2>&1
        $pipExitCode = $LASTEXITCODE
        $pipOutput | ForEach-Object { Write-Host $_ }
        if ($pipExitCode -ne 0) {
            throw "Failed to install Python sidecar dependencies."
        }
    }

    Write-Output $pythonExe
}

if (Test-PortListening -Port $SidecarPort) {
    Write-Host "musio-sidecar already appears to be listening on port $SidecarPort"
    exit 0
}

$PythonExe = Prepare-SidecarPython
$command = "Write-Host 'Starting musio-sidecar'; & '$PythonExe' -m app.main"
$process = Start-Process -FilePath "powershell.exe" -WorkingDirectory $SidecarDir -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
Set-Content -Path (Join-Path $RunDir "musio-sidecar.pid") -Value $process.Id
Write-Host "musio-sidecar launched, pid=$($process.Id)"

if (-not $NoWait) {
    Wait-Http -Name "QQMusic sidecar" -Url $SidecarUrl -TimeoutSeconds 30
}
