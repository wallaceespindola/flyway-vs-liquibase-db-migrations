<#
.SYNOPSIS
    Stops the application started by scripts\start.ps1 on Windows.

.PARAMETER Clean
    Also delete both H2 databases.

.EXAMPLE
    .\scripts\stop.ps1
.EXAMPLE
    .\scripts\stop.ps1 -Clean

.NOTES
    Author: Wallace Espindola
#>
[CmdletBinding()]
param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$PidFile = '.run\app.pid'

function Stop-AppProcess {
    param([int]$ProcessId)

    Write-Host "==> Stopping PID $ProcessId"
    Stop-Process -Id $ProcessId -ErrorAction SilentlyContinue

    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            Write-Host '==> Stopped.'
            return
        }
        Start-Sleep -Milliseconds 500
    }

    Write-Host '==> Still running after 10s, forcing termination'
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

if (Test-Path $PidFile) {
    $appPid = [int](Get-Content $PidFile)
    if (Get-Process -Id $appPid -ErrorAction SilentlyContinue) {
        Stop-AppProcess -ProcessId $appPid
    } else {
        Write-Host "==> No process for PID $appPid, cleaning up stale pid file"
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue
} else {
    # Fall back to matching the java process by its command line, for runs not started by start.ps1.
    $matched = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like '*flyway-vs-liquibase-db-migrations.jar*' }

    if ($matched) {
        foreach ($proc in $matched) { Stop-AppProcess -ProcessId $proc.ProcessId }
    } else {
        Write-Host '==> Application is not running.'
    }
}

if ($Clean -and (Test-Path 'data')) {
    Write-Host '==> Removing H2 databases'
    Remove-Item -Recurse -Force 'data'
}
