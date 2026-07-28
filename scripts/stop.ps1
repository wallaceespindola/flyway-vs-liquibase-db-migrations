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

# Windows recycles pids and Get-Process -Id 0 succeeds (the Idle process), so a stale or empty pid
# file must never be signalled blindly.
function Test-IsOurProcess {
    param([int]$ProcessId)

    if ($ProcessId -le 0) { return $false }
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    return $null -ne $proc -and $proc.CommandLine -like '*flyway-vs-liquibase-db-migrations.jar*'
}

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
    $rawPid = (Get-Content $PidFile -Raw).Trim()
    $appPid = 0
    if (-not [int]::TryParse($rawPid, [ref]$appPid)) { $appPid = 0 }

    if (Test-IsOurProcess -ProcessId $appPid) {
        Stop-AppProcess -ProcessId $appPid
    } else {
        Write-Host "==> PID '$rawPid' is not this application, cleaning up stale pid file without signalling it"
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
