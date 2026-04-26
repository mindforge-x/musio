param(
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root ".musio\run"

$BackendUrl = "http://127.0.0.1:18765/actuator/health"
$FrontendUrl = "http://127.0.0.1:18766/"
$SidecarUrl = "http://127.0.0.1:18767/health"

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Test-PortListening {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -ne $connection
}

function Wait-Http {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

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

function Start-ServiceWindow {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Command,
        [int]$Port
    )

    if (Test-PortListening -Port $Port) {
        Write-Host "$Name already appears to be listening on 127.0.0.1:$Port"
        return
    }

    $wrappedCommand = "Write-Host 'Starting $Name'; $Command"
    $process = Start-Process `
        -FilePath "powershell.exe" `
        -WorkingDirectory $WorkingDirectory `
        -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $wrappedCommand) `
        -PassThru

    Set-Content -Path (Join-Path $RunDir "$Name.pid") -Value $process.Id
    Write-Host "$Name launched in PowerShell window, pid=$($process.Id)"
}

function Assert-Command {
    param(
        [string]$Command,
        [string]$InstallHint
    )

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

    $major = [int]$Matches[1]
    if ($major -lt 21) {
        throw "Java 21+ is required. Current java -version output: $versionOutput"
    }
}

function Assert-Maven {
    Assert-Command -Command "mvn" -InstallHint "Install Maven and ensure mvn is available in PATH."
}

function Assert-Node {
    Assert-Command -Command "npm.cmd" -InstallHint "Install Node.js and ensure npm is available in PATH."
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
            & $candidate.Exe @args 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                return [pscustomobject]$candidate
            }
        } catch {
        }
    }

    return $null
}

function Prepare-SidecarPython {
    param([string]$SidecarDirectory)

    $venvDir = Join-Path $SidecarDirectory ".venv-win"
    $pythonExe = Join-Path $venvDir "Scripts\python.exe"
    $requirements = Join-Path $SidecarDirectory "requirements.txt"

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
        throw "Sidecar venv must use Python 3.11+. Delete $venvDir and rerun the start script."
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

function Prepare-FrontendDependencies {
    param([string]$FrontendDirectory)

    $viteCmd = Join-Path $FrontendDirectory "node_modules\.bin\vite.cmd"
    if (Test-Path $viteCmd) {
        return
    }

    Assert-Node

    Write-Host "Installing Windows frontend dependencies..."
    Push-Location $FrontendDirectory
    try {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed."
        }
    } finally {
        Pop-Location
    }
}

Assert-Java21
Assert-Maven
Assert-Node

$SidecarDir = Join-Path $Root "providers\qqmusic-python-sidecar"
$FrontendDir = Join-Path $Root "frontend"
$BackendDir = Join-Path $Root "backend-spring"

Prepare-FrontendDependencies -FrontendDirectory $FrontendDir
$PythonExe = Prepare-SidecarPython -SidecarDirectory $SidecarDir

$SidecarCommand = "& '$PythonExe' -m app.main"
$BackendCommand = "& mvn spring-boot:run"
$FrontendCommand = "& npm.cmd run dev -- --host 127.0.0.1 --port 18766"

Start-ServiceWindow -Name "musio-sidecar" -WorkingDirectory $SidecarDir -Command $SidecarCommand -Port 18767
Start-ServiceWindow -Name "musio-backend" -WorkingDirectory $BackendDir -Command $BackendCommand -Port 18765
Start-ServiceWindow -Name "musio-frontend" -WorkingDirectory $FrontendDir -Command $FrontendCommand -Port 18766

Wait-Http -Name "QQMusic sidecar" -Url $SidecarUrl -TimeoutSeconds 30
Wait-Http -Name "Spring backend" -Url $BackendUrl -TimeoutSeconds 90
Wait-Http -Name "React frontend" -Url $FrontendUrl -TimeoutSeconds 30

if (-not $NoBrowser) {
    Start-Process $FrontendUrl
}

Write-Host ""
Write-Host "Musio is available at $FrontendUrl"
Write-Host "Stop with: .\scripts\win\stop-windows.ps1"
