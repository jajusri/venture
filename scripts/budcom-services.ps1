# BUDCOM Trust + Relay local controlled-pilot service runner.
#
# Canonical operator surface for starting/stopping/inspecting the backend Trust and Relay services
# locally -- see backend/.env.example for the full environment contract these services read.
#
# Usage:
#   .\scripts\budcom-services.ps1 -Doctor
#   .\scripts\budcom-services.ps1 -Status
#   .\scripts\budcom-services.ps1 -StartTrust
#   .\scripts\budcom-services.ps1 -StartRelay
#   .\scripts\budcom-services.ps1 -Start              # both, Trust first
#   .\scripts\budcom-services.ps1 -Start -Verify
#   .\scripts\budcom-services.ps1 -Verify
#   .\scripts\budcom-services.ps1 -Stop               # only processes THIS tool started
#
# This script never prints secret values (database passwords embedded in a connection string are
# redacted before display) and never stops a process it did not itself start and record (matched by
# PID AND process start time, not PID alone -- PIDs are reused by the OS).

[CmdletBinding()]
param(
    [switch]$Doctor,
    [switch]$Status,
    [switch]$StartTrust,
    [switch]$StartRelay,
    [switch]$Start,
    [switch]$Verify,
    [switch]$Stop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BackendDir = Join-Path $RepoRoot 'backend'
$StateDir = Join-Path $env:TEMP 'budcom-services'
New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

function Get-EnvOrDefault {
    param([string]$Name, [string]$Default)
    $value = [System.Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value
}

function Redact-ConnectionString {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return '(not set)' }
    return ($Value -replace '://([^:@/]+):([^@/]+)@', '://$1:***@')
}

function Get-DatabaseHostPort {
    param([string]$ConnectionString)
    if ([string]::IsNullOrWhiteSpace($ConnectionString)) { return $null }
    if ($ConnectionString -match '@([^:/@]+)(?::(\d+))?/') {
        $dbHost = $Matches[1]
        $port = if ($Matches[2]) { [int]$Matches[2] } else { 5432 }
        return [pscustomobject]@{ HostName = $dbHost; Port = $port }
    }
    return $null
}

$TrustPort = [int](Get-EnvOrDefault 'BUDCOM_TRUST_PORT' '8080')
$RelayPort = [int](Get-EnvOrDefault 'BUDCOM_RELAY_PORT' '8082')
$TrustHost = Get-EnvOrDefault 'BUDCOM_TRUST_HOST' '127.0.0.1'
$RelayHostName = Get-EnvOrDefault 'BUDCOM_RELAY_HOST' '127.0.0.1'
$TrustDbUrl = Get-EnvOrDefault 'BUDCOM_TRUST_DATABASE_URL' ''
$RelayDbUrl = Get-EnvOrDefault 'BUDCOM_RELAY_DATABASE_URL' $TrustDbUrl

function Test-PortListening {
    param([string]$TargetHost, [int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return [bool]$conn
}

function Test-TcpReachable {
    param([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 800)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $task = $client.ConnectAsync($TargetHost, $Port)
        $completed = $task.Wait($TimeoutMs)
        $client.Close()
        # Not $task.IsCompletedSuccessfully: that property resolves to an empty string (falsy) under
        # Windows PowerShell 5.1's reflection on System.Threading.Tasks.Task here, producing a false
        # "unreachable" even when the connection genuinely succeeded. Status is reliable in both.
        return $completed -and $task.Status -eq [System.Threading.Tasks.TaskStatus]::RanToCompletion
    } catch { return $false }
}

function Get-TrackedProcess {
    param([string]$Name)
    $pidFile = Join-Path $StateDir "$Name.pid"
    if (-not (Test-Path $pidFile)) { return $null }
    $recorded = Get-Content -Path $pidFile -Raw | ConvertFrom-Json
    $proc = Get-Process -Id $recorded.pid -ErrorAction SilentlyContinue
    if (-not $proc) { return $null }
    # PIDs are reused by the OS -- only trust this process if its own start time still matches what
    # we recorded when we launched it, so a coincidentally-reused PID is never mistaken for ours.
    if ($proc.StartTime.ToString('o') -ne $recorded.startTime) { return $null }
    return $proc
}

function Start-BudcomService {
    param([string]$Name, [string]$NpmScript, [int]$Port, [string]$TargetHost)
    $existing = Get-TrackedProcess -Name $Name
    if ($existing) {
        Write-Host "[$Name] already running (pid $($existing.Id)), not starting a second instance."
        return
    }
    if (Test-PortListening -TargetHost $TargetHost -Port $Port) {
        Write-Host "FAIL: [$Name] port $Port is already in use by a process this tool did not start. Refusing to start a second listener on the same port." -ForegroundColor Red
        Write-Host "      Free the port, or set BUDCOM_$($Name.ToUpper())_PORT to a different value, then retry."
        return
    }
    $logPath = Join-Path $StateDir "$Name.log"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList "/c npm run $NpmScript" -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $logPath -RedirectStandardError "$logPath.err" -PassThru -WindowStyle Hidden
    # `npm run` -> `tsx` startup (transpile + module load) genuinely takes over 300ms before a bad
    # config (e.g. an unreachable database) actually surfaces as a crash -- poll instead of a single
    # short sleep, so a real early failure is still caught rather than misreported as "started".
    $deadline = (Get-Date).AddSeconds(3)
    while ((Get-Date) -lt $deadline -and -not $proc.HasExited) { Start-Sleep -Milliseconds 200 }
    if ($proc.HasExited) {
        Write-Host "FAIL: [$Name] exited within 3s (exit code $($proc.ExitCode)). See $logPath / $logPath.err" -ForegroundColor Red
        Write-Host '--- last error output ---'
        Get-Content "$logPath.err" -ErrorAction SilentlyContinue | Select-Object -Last 10 | ForEach-Object { Write-Host "  $_" }
        return
    }
    [pscustomobject]@{ pid = $proc.Id; startTime = $proc.StartTime.ToString('o'); npmScript = $NpmScript } |
        ConvertTo-Json | Set-Content -Path (Join-Path $StateDir "$Name.pid")
    Write-Host "[$Name] started (pid $($proc.Id)). Log: $logPath"
}

function Get-DescendantProcessIds {
    # `Start-Process cmd.exe /c "npm run ..."` spawns a process TREE (cmd -> npm -> node); Windows
    # does not cascade-terminate children when only the top PID is stopped (no job-object grouping
    # here), so a plain `Stop-Process` on the tracked PID alone silently orphans the real node.exe
    # underneath -- it keeps running, keeps the port bound, and keeps serving whatever it was serving
    # (stale env/config) even though `-Status`/`-Stop` both reported success. Walk the WMI parent-child
    # chain recursively so every descendant is found regardless of tree depth.
    param([int]$RootProcessId)
    $all = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
    $result = New-Object System.Collections.Generic.List[int]
    $frontier = New-Object System.Collections.Generic.Queue[int]
    $frontier.Enqueue($RootProcessId)
    while ($frontier.Count -gt 0) {
        $current = $frontier.Dequeue()
        foreach ($child in ($all | Where-Object { $_.ParentProcessId -eq $current })) {
            if (-not $result.Contains([int]$child.ProcessId)) {
                $result.Add([int]$child.ProcessId)
                $frontier.Enqueue([int]$child.ProcessId)
            }
        }
    }
    return $result
}

function Stop-BudcomService {
    param([string]$Name)
    $proc = Get-TrackedProcess -Name $Name
    $pidFile = Join-Path $StateDir "$Name.pid"
    if (-not $proc) {
        Write-Host "[$Name] not running (or not started by this tool) -- nothing to stop."
        if (Test-Path $pidFile) { Remove-Item -Force $pidFile }
        return
    }
    $descendants = @(Get-DescendantProcessIds -RootProcessId $proc.Id)
    Write-Host "[$Name] stopping pid $($proc.Id)$(if ($descendants.Count) { " and $($descendants.Count) descendant process(es) ($($descendants -join ', '))" })..."
    foreach ($descendantId in $descendants) {
        Stop-Process -Id $descendantId -Force -Confirm:$false -ErrorAction SilentlyContinue
    }
    Stop-Process -Id $proc.Id -Force -Confirm:$false -ErrorAction SilentlyContinue
    Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
    Write-Host "[$Name] stopped."
}

function Invoke-HealthCheck {
    param([string]$Name, [string]$TargetHost, [int]$Port)
    $url = "http://${TargetHost}:${Port}/health"
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
        if ($response.status -eq 'ok') {
            Write-Host "[$Name] health: OK ($($response.service) at $url)" -ForegroundColor Green
            return $true
        }
        Write-Host "[$Name] health: unexpected response from $url" -ForegroundColor Yellow
        return $false
    } catch {
        Write-Host "[$Name] health: UNREACHABLE at $url -- $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Show-Doctor {
    Write-Host '=== BUDCOM Trust + Relay doctor (read-only) ==='
    if (-not (Test-Path $BackendDir)) { Write-Host "FAIL: backend directory missing: $BackendDir" -ForegroundColor Red; return }
    Write-Host "Backend project: $BackendDir"
    $nodeModules = Join-Path $BackendDir 'node_modules'
    Write-Host "Dependencies installed: $(if (Test-Path $nodeModules) { 'YES' } else { 'NO -- run `npm install` in backend/' })"

    Write-Host "`n-- Configuration (non-secret) --"
    Write-Host "BUDCOM_TRUST_DATABASE_URL: $(Redact-ConnectionString $TrustDbUrl)"
    Write-Host "BUDCOM_RELAY_DATABASE_URL: $(Redact-ConnectionString $RelayDbUrl) $(if (-not (Get-EnvOrDefault 'BUDCOM_RELAY_DATABASE_URL' '')) { '(defaults to Trust''s URL)' })"
    Write-Host "Trust:  $TrustHost`:$TrustPort"
    Write-Host "Relay:  $RelayHostName`:$RelayPort"

    Write-Host "`n-- Ports --"
    foreach ($p in @(@{n='Trust';port=$TrustPort;h=$TrustHost}, @{n='Relay';port=$RelayPort;h=$RelayHostName})) {
        $listening = Test-PortListening -TargetHost $p.h -Port $p.port
        Write-Host "$($p.n) port $($p.port): $(if ($listening) { 'IN USE (something is already listening)' } else { 'free' })"
    }

    Write-Host "`n-- Database reachability --"
    if ([string]::IsNullOrWhiteSpace($TrustDbUrl)) {
        Write-Host 'FAIL: BUDCOM_TRUST_DATABASE_URL is not set -- Trust cannot start without it. See backend/.env.example.' -ForegroundColor Red
    } else {
        $target = Get-DatabaseHostPort -ConnectionString $TrustDbUrl
        if (-not $target) {
            Write-Host 'WARN: could not parse host/port out of BUDCOM_TRUST_DATABASE_URL to test reachability.' -ForegroundColor Yellow
        } elseif (Test-TcpReachable -TargetHost $target.HostName -Port $target.Port) {
            Write-Host "PASS: TCP reachable at $($target.HostName):$($target.Port) (this only proves something is listening, not that credentials/schema are valid)." -ForegroundColor Green
        } else {
            Write-Host "FAIL: nothing reachable at $($target.HostName):$($target.Port). Start PostgreSQL, or point BUDCOM_TRUST_DATABASE_URL at a reachable instance." -ForegroundColor Red
        }
    }

    Write-Host "`n-- Node/npm --"
    try { Write-Host "node: $(node -v)" } catch { Write-Host 'FAIL: node not found in PATH' -ForegroundColor Red }
    try { Write-Host "npm:  $(npm -v)" } catch { Write-Host 'FAIL: npm not found in PATH' -ForegroundColor Red }
}

function Show-Status {
    Write-Host '=== BUDCOM Trust + Relay status ==='
    foreach ($svc in @(
        @{ name = 'trust'; port = $TrustPort; targetHost = $TrustHost },
        @{ name = 'relay'; port = $RelayPort; targetHost = $RelayHostName }
    )) {
        $proc = Get-TrackedProcess -Name $svc.name
        $listening = Test-PortListening -TargetHost $svc.targetHost -Port $svc.port
        Write-Host "[$($svc.name)] tracked process: $(if ($proc) { "running (pid $($proc.Id))" } else { 'not running (or not started by this tool)' })"
        Write-Host "[$($svc.name)] port $($svc.port): $(if ($listening) { 'listening' } else { 'not listening' })"
    }
}

if (-not ($Doctor -or $Status -or $StartTrust -or $StartRelay -or $Start -or $Verify -or $Stop)) {
    Write-Host 'No mode selected. Examples:'
    Write-Host '  .\scripts\budcom-services.ps1 -Doctor'
    Write-Host '  .\scripts\budcom-services.ps1 -Start -Verify'
    Write-Host '  .\scripts\budcom-services.ps1 -Status'
    Write-Host '  .\scripts\budcom-services.ps1 -Stop'
    exit 0
}

if ($Doctor) { Show-Doctor }
if ($StartTrust -or $Start) { Start-BudcomService -Name 'trust' -NpmScript 'dev' -Port $TrustPort -TargetHost $TrustHost }
if ($StartRelay -or $Start) { Start-BudcomService -Name 'relay' -NpmScript 'dev:relay' -Port $RelayPort -TargetHost $RelayHostName }
if ($Status) { Show-Status }
if ($Verify) {
    Start-Sleep -Milliseconds 500
    $trustOk = Invoke-HealthCheck -Name 'trust' -TargetHost $TrustHost -Port $TrustPort
    $relayOk = Invoke-HealthCheck -Name 'relay' -TargetHost $RelayHostName -Port $RelayPort
    if (-not ($trustOk -and $relayOk)) { exit 1 }
}
if ($Stop) {
    Stop-BudcomService -Name 'trust'
    Stop-BudcomService -Name 'relay'
}
exit 0
