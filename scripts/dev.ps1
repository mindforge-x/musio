$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "win\start-windows.ps1") @args
