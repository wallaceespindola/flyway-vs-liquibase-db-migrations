<#
.SYNOPSIS
    One-command startup for Windows (PowerShell 5.1+ / PowerShell 7+).

.DESCRIPTION
    Builds the jar if needed, starts the application, waits for it to become healthy and prints the
    URLs. Use scripts\stop.ps1 to shut it down.

.PARAMETER Foreground
    Run in the foreground instead of the background. Ctrl-C stops it.

.PARAMETER Clean
    Delete both H2 databases first, so both engines migrate from scratch.

.EXAMPLE
    .\scripts\start.ps1
.EXAMPLE
    .\scripts\start.ps1 -Clean
.EXAMPLE
    .\scripts\start.ps1 -Foreground

.NOTES
    Author: Wallace Espindola
#>
[CmdletBinding()]
param(
    [switch]$Foreground,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
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

$versionLine = (& java -version 2>&1)[0]
if ($versionLine -match 'version "(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 21) {
        throw "Java 21 or newer is required, found Java $javaMajor."
    }
}

if (Test-Path $PidFile) {
    $existingPid = Get-Content $PidFile
    if (Get-Process -Id $existingPid -ErrorAction SilentlyContinue) {
        Write-Host "Application is already running (PID $existingPid). Run scripts\stop.ps1 first."
        exit 1
    }
}

if ($Clean -and (Test-Path 'data')) {
    Write-Host '==> Removing H2 databases so both engines migrate from scratch'
    Remove-Item -Recurse -Force 'data'
}

$Maven = Resolve-Maven
Write-Host "==> Building $Jar"
& $Maven -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }

if ($Foreground) {
    Write-Host "==> Starting in the foreground on port $Port (Ctrl-C to stop)"
    & java -jar $Jar --server.port=$Port
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

Write-Host "==> Starting in the background on port $Port"
$process = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $Jar, "--server.port=$Port") `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err" `
    -NoNewWindow -PassThru
$process.Id | Set-Content $PidFile

Write-Host '==> Waiting for the application to become healthy' -NoNewline
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/health" -TimeoutSec 2 | Out-Null
        Write-Host ''
        Write-Host '==> Ready.'
        Write-Host ''
        Write-Host "  Dashboard    http://localhost:$Port/"
        Write-Host "  Swagger UI   http://localhost:$Port/swagger-ui.html"
        Write-Host "  Comparison   http://localhost:$Port/api/v1/comparison"
        Write-Host "  H2 console   http://localhost:$Port/h2-console"
        Write-Host "  Logs         $LogFile"
        Write-Host ''
        Write-Host '  Stop with:   .\scripts\stop.ps1'
        exit 0
    } catch {
        if ($process.HasExited) {
            Write-Host ''
            Write-Error 'The application exited during startup. Last 40 log lines:'
            if (Test-Path $LogFile) { Get-Content $LogFile -Tail 40 }
            Remove-Item $PidFile -ErrorAction SilentlyContinue
            exit 1
        }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 1
    }
}

Write-Host ''
Write-Error 'Timed out waiting for health. Last 40 log lines:'
if (Test-Path $LogFile) { Get-Content $LogFile -Tail 40 }
exit 1
