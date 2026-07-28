<#
.SYNOPSIS
    One-command startup for Windows (PowerShell 5.1+ / PowerShell 7+).

.DESCRIPTION
    Builds the jar if needed, starts the application, waits for it to become healthy and prints the
    URLs. Use stop.ps1 to shut it down.

.PARAMETER Foreground
    Run in the foreground instead of the background. Ctrl-C stops it.

.PARAMETER Clean
    Delete both H2 databases first, so both engines migrate from scratch.

.EXAMPLE
    .\start.ps1
.EXAMPLE
    .\start.ps1 -Clean
.EXAMPLE
    .\start.ps1 -Foreground

.NOTES
    Author: Wallace Espindola
#>
[CmdletBinding()]
param(
    [switch]$Foreground,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

$Port    = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
$Jar     = 'target\flyway-vs-liquibase-db-migrations.jar'
$RunDir  = '.run'
$PidFile = Join-Path $RunDir 'app.pid'
$LogFile = Join-Path $RunDir 'app.log'

function Resolve-Maven {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return 'mvn' }
    if (Test-Path '.\mvnw.cmd') { return '.\mvnw.cmd' }
    throw 'Maven not found. Install Maven or add the Maven wrapper.'
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'java not found on PATH. Java 21 or newer is required.'
}

# `java -version` writes to stderr. Under Windows PowerShell 5.1, merging that with 2>&1 while
# $ErrorActionPreference is 'Stop' turns the output into a terminating NativeCommandError, so the
# preference is relaxed just for this call.
$versionLine = $null
try {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $versionLine = (& java -version 2>&1 | Out-String)
} finally {
    $ErrorActionPreference = $previousPreference
}

if ($versionLine -match 'version "(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 21) {
        throw "Java 21 or newer is required, found Java $javaMajor."
    }
}

# A pid file can outlive its process and Windows recycles pids, so confirm the process really is
# this application before trusting it. Note Get-Process -Id 0 succeeds (the Idle process), which is
# why an empty or malformed pid file must not be coerced straight to [int].
function Test-IsOurProcess {
    param([int]$ProcessId)

    if ($ProcessId -le 0) { return $false }
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    return $null -ne $proc -and $proc.CommandLine -like '*flyway-vs-liquibase-db-migrations.jar*'
}

if (Test-Path $PidFile) {
    $rawPid = (Get-Content $PidFile -Raw).Trim()
    $existingPid = 0
    if (-not [int]::TryParse($rawPid, [ref]$existingPid)) { $existingPid = 0 }

    if (Test-IsOurProcess -ProcessId $existingPid) {
        Write-Host "Application is already running (PID $existingPid). Run stop.ps1 first."
        exit 1
    }
    Write-Host "==> Ignoring stale pid file (PID '$rawPid' is not this application)"
    Remove-Item $PidFile -ErrorAction SilentlyContinue
}

if ($Clean -and (Test-Path 'data')) {
    Write-Host '==> Removing H2 databases so both engines migrate from scratch'
    Remove-Item -Recurse -Force 'data'
}

$Maven = Resolve-Maven
Write-Host "==> Building $Jar"
& $Maven -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }

# Native commands and Start-Process resolve relative paths against [Environment]::CurrentDirectory,
# which Set-Location does NOT update. Without absolute paths and an explicit -WorkingDirectory, the
# log files and the ./data databases can land wherever the shell was originally launched from, while
# stop.ps1 looks for the pid file under the project root.
$AbsJar    = Join-Path $ProjectRoot $Jar
$AbsLog    = Join-Path $ProjectRoot $LogFile
$AbsErrLog = "$AbsLog.err"

if ($Foreground) {
    Write-Host "==> Starting in the foreground on port $Port (Ctrl-C to stop)"
    & java -jar $AbsJar --server.port=$Port
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

Write-Host "==> Starting in the background on port $Port"
$process = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $AbsJar, "--server.port=$Port") `
    -WorkingDirectory $ProjectRoot `
    -RedirectStandardOutput $AbsLog `
    -RedirectStandardError $AbsErrLog `
    -NoNewWindow -PassThru
$process.Id | Set-Content $PidFile

# Startup failures (bad jar path, JVM crash, missing main class) go to stderr, so both streams have
# to be shown when reporting a failure.
function Show-StartupLogs {
    foreach ($file in @($AbsLog, $AbsErrLog)) {
        if ((Test-Path $file) -and (Get-Item $file).Length -gt 0) {
            Write-Host "--- $file (last 40 lines) ---"
            Get-Content $file -Tail 40
        }
    }
}

Write-Host '==> Waiting for the application to become healthy' -NoNewline
for ($i = 0; $i -lt 60; $i++) {
    try {
        # Not /api/v1/health: it returns 503 when a database is unreachable, which would make a
        # running-but-degraded app look like it never started.
        Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -TimeoutSec 2 | Out-Null
        Write-Host ''
        Write-Host '==> Ready.'
        Write-Host ''
        Write-Host "  Dashboard    http://localhost:$Port/"
        Write-Host "  Swagger UI   http://localhost:$Port/swagger-ui.html"
        Write-Host "  Comparison   http://localhost:$Port/api/v1/comparison"
        Write-Host "  H2 console   http://localhost:$Port/h2-console"
        Write-Host "  Logs         $LogFile"
        Write-Host ''
        Write-Host '  Stop with:   .\stop.ps1'
        exit 0
    } catch {
        if ($process.HasExited) {
            Write-Host ''
            Write-Error 'The application exited during startup.'
            Show-StartupLogs
            Remove-Item $PidFile -ErrorAction SilentlyContinue
            exit 1
        }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 1
    }
}

Write-Host ''
Write-Error 'Timed out waiting for health.'
Show-StartupLogs
exit 1
