# Canonical BUDCOM Desktop developer launcher (read-only Status, bounded Build/Launch/Verify/Restart).
# Usage:
#   .\scripts\budcom-desktop.ps1 -Status
#   .\scripts\budcom-desktop.ps1 -Build -Launch -Verify
#   .\scripts\budcom-desktop.ps1 -Restart -Verify

[CmdletBinding()]
param(
    [switch]$Status,
    [switch]$Build,
    [switch]$Launch,
    [switch]$Verify,
    [switch]$Restart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --- Configuration (discovered from repository layout) ---
$Script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Script:DesktopDir = Join-Path $RepoRoot 'apps\budcom_desktop'
$Script:MainOutput = Join-Path $DesktopDir 'dist\main\main.js'
$Script:ElectronExe = Join-Path $DesktopDir 'node_modules\electron\dist\electron.exe'
$Script:PackageName = '@budcom/desktop'
$Script:AppId = 'com.budcom.desktop'
$Script:ProductName = 'Budcom Desktop'
$Script:WindowTitle = 'Business OS Tally Connector'
$Script:UserDataDir = Join-Path $env:APPDATA '@budcom\desktop'
$Script:DesktopConfigPath = Join-Path $UserDataDir 'desktop-config.json'
$Script:DesktopLogPath = Join-Path $UserDataDir 'logs\budcom-desktop.log'
$Script:LauncherLogDir = Join-Path $env:TEMP 'budcom-desktop-launcher'
$Script:DefaultConnectorPort = 8080

$Script:BuildTimeoutSec = 600
$Script:StartupTimeoutSec = 90
$Script:ShutdownTimeoutSec = 30
$Script:VerifyAliveSec = 5
$Script:HeartbeatSec = 15
$Script:MaxSilentWaitSec = 120

$Script:LauncherLogFile = $null
$Script:LastExitCode = 0

function Initialize-LauncherLog {
    New-Item -ItemType Directory -Force -Path $LauncherLogDir | Out-Null
    $Script:LauncherLogFile = Join-Path $LauncherLogDir ("launch-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))
}

function Write-LauncherLog {
    param([string]$Message)
    $line = "[{0:yyyy-MM-dd HH:mm:ss}] {1}" -f (Get-Date), $Message
    if ($LauncherLogFile) {
        Add-Content -Path $LauncherLogFile -Value $line -Encoding UTF8
    }
}

function Write-Step {
    param([string]$Message)
    Write-Host $Message
    Write-LauncherLog $Message
}

function Write-Fail {
    param([string]$Message)
    Write-Host "FAILED: $Message" -ForegroundColor Red
    Write-LauncherLog "FAILED: $Message"
    if ($LauncherLogFile) {
        Write-Host "Launcher log: $LauncherLogFile"
    }
    exit 1
}

function Get-RepoGitState {
    Push-Location $RepoRoot
    try {
        return [ordered]@{
            Branch = (git branch --show-current 2>$null)
            Head = (git rev-parse HEAD 2>$null)
            Dirty = [bool](git status --short --untracked-files=all 2>$null)
            StatusShort = (git status --short --untracked-files=all 2>$null)
        }
    }
    finally {
        Pop-Location
    }
}

function Test-RepoMarker {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $false }
    $normalizedRepo = $RepoRoot.Replace('/', '\')
    $markers = @(
        $normalizedRepo,
        'budcom_desktop',
        'Budcom Desktop.exe',
        '@budcom\desktop',
        'com.budcom.desktop'
    )
    foreach ($marker in $markers) {
        if ($Text.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

function Get-BudcomDesktopProcesses {
    $results = @()
    $cim = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -eq 'Budcom Desktop.exe' -or
            ($_.Name -eq 'electron.exe' -and (Test-RepoMarker $_.CommandLine))
        })
    foreach ($proc in $cim) {
        $results += [pscustomobject]@{
            ProcessId = $proc.ProcessId
            Name = $proc.Name
            CommandLine = $proc.CommandLine
            Kind = if ($proc.Name -eq 'Budcom Desktop.exe') { 'packaged' } else { 'dev-electron' }
        }
    }
    return $results
}

function Get-BudcomConnectorProcesses {
    $results = @()
    $cim = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -and
            $_.CommandLine -match 'budcom_connector[\\/]+dist[\\/]+main\.js' -and
            (Test-RepoMarker $_.CommandLine)
        })
    foreach ($proc in $cim) {
        $results += [pscustomobject]@{
            ProcessId = $proc.ProcessId
            Name = $proc.Name
            CommandLine = $proc.CommandLine
        }
    }
    return $results
}

function Get-ConnectorPort {
    if (-not (Test-Path $DesktopConfigPath)) {
        return $DefaultConnectorPort
    }
    try {
        $config = Get-Content -Raw -Path $DesktopConfigPath | ConvertFrom-Json
        if ($config.connectorPort) { return [int]$config.connectorPort }
        if ($config.effective.connectorPort) { return [int]$config.effective.connectorPort }
    }
    catch {
        Write-LauncherLog "Could not parse desktop config for connector port: $($_.Exception.Message)"
    }
    return $DefaultConnectorPort
}

function Test-PortListening {
    param([int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return [bool]$conn
}

function Get-BuildOutputState {
    $exists = Test-Path $MainOutput
    $state = [ordered]@{
        Exists = $exists
        Path = $MainOutput
        Modified = $null
        SourceNewer = $null
    }
    if (-not $exists) { return $state }
    $built = Get-Item $MainOutput
    $state.Modified = $built.LastWriteTime
    $srcRoot = Join-Path $DesktopDir 'src'
    $newestSource = Get-ChildItem -Path $srcRoot -Recurse -File -Include *.ts,*.tsx,*.mjs -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($newestSource) {
        $state.SourceNewer = ($newestSource.LastWriteTime -gt $built.LastWriteTime)
    }
    return $state
}

function Read-DesktopStartupLogTail {
    param([int]$MaxLines = 40)
    if (-not (Test-Path $DesktopLogPath)) {
        return @{ Present = $false; Fatal = $false; Lines = @() }
    }
    $lines = Get-Content -Path $DesktopLogPath -Tail $MaxLines -ErrorAction SilentlyContinue
    $fatal = $false
    foreach ($line in $lines) {
        if ($line -match 'uncaughtException|\[budcom-desktop:startup\] uncaughtException|Fatal bootstrap error') {
            $fatal = $true
            break
        }
    }
    return @{ Present = $true; Fatal = $fatal; Lines = $lines }
}

function Invoke-BudcomBuild {
    Write-Step '[3/6] Build - npm run build (apps/budcom_desktop)'
    if (-not (Test-Path (Join-Path $DesktopDir 'package.json'))) {
        Write-Fail "Desktop package.json not found at $DesktopDir"
    }

    $logPath = Join-Path $LauncherLogDir ("build-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = '/c npm run build'
    $psi.WorkingDirectory = $DesktopDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    if ($env:ELECTRON_RUN_AS_NODE) {
        $psi.Environment['ELECTRON_RUN_AS_NODE'] = $null
    }

    $started = Get-Date
    $proc = [System.Diagnostics.Process]::Start($psi)
    $lastHeartbeat = Get-Date
    while (-not $proc.HasExited) {
        $elapsed = (Get-Date) - $started
        if ($elapsed.TotalSeconds -gt $BuildTimeoutSec) {
            try { $proc.Kill() } catch { }
            Write-Fail "Build exceeded ${BuildTimeoutSec}s timeout. See $logPath"
        }
        if (((Get-Date) - $lastHeartbeat).TotalSeconds -ge $HeartbeatSec) {
            Write-Step ("  build still running ({0:N0}s elapsed)..." -f $elapsed.TotalSeconds)
            $lastHeartbeat = Get-Date
        }
        Start-Sleep -Milliseconds 500
    }

    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    @($stdout, $stderr, "exit=$($proc.ExitCode)", "elapsed=$(((Get-Date)-$started).TotalSeconds)s") |
        Set-Content -Path $logPath -Encoding UTF8

    if ($proc.ExitCode -ne 0) {
        Write-Fail "Build failed (exit $($proc.ExitCode)). Log: $logPath"
    }

    if (-not (Test-Path $MainOutput)) {
        Write-Fail "Build reported success but missing $MainOutput. Log: $logPath"
    }

    Write-Step ("  build succeeded in {0:N1}s; log: {1}" -f ((Get-Date)-$started).TotalSeconds, $logPath)
}

function Stop-BudcomDesktopProcesses {
    param([string]$Reason)
    $desktop = @(Get-BudcomDesktopProcesses)
    $connector = @(Get-BudcomConnectorProcesses)
    if ($desktop.Count -eq 0 -and $connector.Count -eq 0) {
        Write-Step "  no BUDCOM desktop/connector processes to stop ($Reason)"
        return
    }

    Write-Step ("  stopping {0} desktop + {1} connector process(es) ({2})" -f $desktop.Count, $connector.Count, $Reason)
    foreach ($proc in ($desktop + $connector)) {
        try {
            $p = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
            if (-not $p) { continue }
            if ($p.MainWindowHandle -ne 0) {
                $null = $p.CloseMainWindow()
            }
        }
        catch {
            Write-LauncherLog "CloseMainWindow failed for PID $($proc.ProcessId): $($_.Exception.Message)"
        }
    }

    $deadline = (Get-Date).AddSeconds($ShutdownTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (@(Get-BudcomDesktopProcesses).Count -eq 0 -and @(Get-BudcomConnectorProcesses).Count -eq 0) {
            Write-Step '  processes stopped gracefully'
            return
        }
        Start-Sleep -Seconds 1
    }

    foreach ($proc in (Get-BudcomDesktopProcesses + Get-BudcomConnectorProcesses)) {
        try {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
            Write-LauncherLog "Force-stopped PID $($proc.ProcessId)"
        }
        catch {
            Write-LauncherLog "Force-stop failed for PID $($proc.ProcessId): $($_.Exception.Message)"
        }
    }
}

function Invoke-BudcomLaunch {
    Write-Step '[4/6] Launch - electron . (detached)'

    if ($env:ELECTRON_RUN_AS_NODE) {
        Write-Step '  clearing ELECTRON_RUN_AS_NODE for child process (required for Electron GUI)'
        Remove-Item Env:ELECTRON_RUN_AS_NODE -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path $ElectronExe)) {
        Write-Fail "Electron binary missing at $ElectronExe - run npm install in apps/budcom_desktop"
    }
    if (-not (Test-Path $MainOutput)) {
        Write-Fail "Build output missing at $MainOutput - run with -Build first"
    }

    $running = @(Get-BudcomDesktopProcesses)
    if ($running.Count -gt 0) {
        Write-Step ("  desktop already running ({0} process(es)); single-instance lock will focus existing window" -f $running.Count)
        foreach ($p in $running) {
            Write-Step ("    PID {0} ({1})" -f $p.ProcessId, $p.Kind)
        }
        # Trigger second-instance focus via another short-lived launch attempt.
        $probe = Start-Process -FilePath $ElectronExe -ArgumentList '.' -WorkingDirectory $DesktopDir -PassThru
        Start-Sleep -Seconds 2
        if ($probe -and -not $probe.HasExited) {
            try { $probe.Kill() } catch { }
        }
        return
    }

    $proc = Start-Process -FilePath $ElectronExe -ArgumentList '.' -WorkingDirectory $DesktopDir -PassThru
    Write-Step ("  started electron PID {0}" -f $proc.Id)
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        $log = Read-DesktopStartupLogTail
        Write-Fail "Electron exited immediately (exit $($proc.ExitCode)). Desktop log fatal=$($log.Fatal). Launcher log: $LauncherLogFile"
    }
}

function Invoke-BudcomVerify {
    Write-Step '[5/6] Verify - process + startup stability'
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSec)
    $observedPid = $null
    while ((Get-Date) -lt $deadline) {
        $running = @(Get-BudcomDesktopProcesses)
        if ($running.Count -gt 0) {
            $observedPid = $running[0].ProcessId
            break
        }
        Start-Sleep -Seconds 1
    }

    if (-not $observedPid) {
        $log = Read-DesktopStartupLogTail
        Write-Fail "No BUDCOM desktop process detected within ${StartupTimeoutSec}s. Desktop log fatal=$($log.Fatal). Launcher log: $LauncherLogFile"
    }

    Write-Step ("  desktop process alive: PID {0}" -f $observedPid)
    Start-Sleep -Seconds $VerifyAliveSec
    $still = Get-BudcomDesktopProcesses | Where-Object { $_.ProcessId -eq $observedPid }
    if (-not $still) {
        $log = Read-DesktopStartupLogTail
        Write-Fail "Desktop process exited during ${VerifyAliveSec}s observation. Desktop log fatal=$($log.Fatal). Launcher log: $LauncherLogFile"
    }

    $log = Read-DesktopStartupLogTail
    if ($log.Fatal) {
        Write-Fail "Desktop log shows fatal startup error. See $DesktopLogPath and $LauncherLogFile"
    }

    $port = Get-ConnectorPort
    $listening = Test-PortListening -Port $port
    Write-Step ("  connector port {0}: {1}" -f $port, $(if ($listening) { 'LISTENING' } else { 'not listening (may still be starting)' }))

    if ($listening) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 5
            Write-Step ("  connector /health: status=$($health.status) bindHost=$($health.bindHost)")
        }
        catch {
            Write-Step "  connector /health not ready yet (non-fatal for GUI launch)"
        }
    }

    Write-Step '[6/6] Result - verification passed (launcher detaching; GUI continues independently)'
}

function Show-BudcomStatus {
    Write-Step '=== BUDCOM Desktop Status ==='
    $git = Get-RepoGitState
    Write-Step ("Git HEAD: {0}" -f $git.Head)
    Write-Step ("Git branch: {0}" -f $git.Branch)
    Write-Step ("Worktree: {0}" -f $(if ($git.Dirty) { 'DIRTY' } else { 'clean' }))
    if ($git.Dirty) {
        Write-Step 'Dirty files:'
        $git.StatusShort | ForEach-Object { Write-Step "  $_" }
    }

    Write-Step ("Desktop project: {0}" -f $DesktopDir)
    Write-Step 'Build system: npm + TypeScript + Electron (apps/budcom_desktop/package.json)'
    Write-Step ("Package: {0} ({1})" -f $PackageName, $AppId)
    Write-Step ("Product name: {0}" -f $ProductName)
    Write-Step ("Electron binary: {0}" -f $(if (Test-Path $ElectronExe) { $ElectronExe } else { '(missing - npm install required)' }))

    $build = Get-BuildOutputState
    Write-Step ("Build output: {0}" -f $(if ($build.Exists) { 'present' } else { 'missing' }))
    if ($build.Exists) {
        Write-Step ("  path: {0}" -f $build.Path)
        Write-Step ("  modified: {0}" -f $build.Modified)
        if ($null -ne $build.SourceNewer) {
            Write-Step ("  source newer than build: {0}" -f $build.SourceNewer)
        }
    }

    $desktop = @(Get-BudcomDesktopProcesses)
    Write-Step ("Desktop running: {0}" -f $(if ($desktop.Count -gt 0) { 'YES' } else { 'NO' }))
    foreach ($p in $desktop) {
        Write-Step ("  PID {0} [{1}]" -f $p.ProcessId, $p.Kind)
    }

    $connector = @(Get-BudcomConnectorProcesses)
    Write-Step ("Connector child processes (repo-scoped): {0}" -f $connector.Count)
    foreach ($p in $connector) {
        Write-Step ("  PID {0} ({1})" -f $p.ProcessId, $p.Name)
    }

    $port = Get-ConnectorPort
    Write-Step ("Connector port (config): {0}" -f $port)
    Write-Step ("Port listening: {0}" -f $(Test-PortListening -Port $port))
    Write-Step ("User data: {0}" -f $UserDataDir)
    Write-Step ("Desktop log: {0}" -f $(if (Test-Path $DesktopLogPath) { $DesktopLogPath } else { '(not created yet)' }))
    Write-Step ("Launcher logs: {0}" -f $LauncherLogDir)
    if ($env:ELECTRON_RUN_AS_NODE) {
        Write-Step 'WARNING: ELECTRON_RUN_AS_NODE is set in this shell - Launch clears it for the child process'
    }
}

# --- Main ---
Initialize-LauncherLog

if (-not ($Status -or $Build -or $Launch -or $Verify -or $Restart)) {
    Write-Step 'No mode selected. Examples:'
    Write-Step '  .\scripts\budcom-desktop.ps1 -Status'
    Write-Step '  .\scripts\budcom-desktop.ps1 -Build -Launch -Verify'
    Write-Step '  .\scripts\budcom-desktop.ps1 -Restart -Verify'
    exit 0
}

Write-Step '[1/6] Repository check'
$git = Get-RepoGitState
Write-Step ("  HEAD {0} on {1} ({2})" -f $git.Head, $git.Branch, $(if ($git.Dirty) { 'dirty' } else { 'clean' }))

Write-Step '[2/6] Desktop configuration'
if (-not (Test-Path $DesktopDir)) {
    Write-Fail "Desktop project not found at $DesktopDir"
}

if ($Status) {
    Show-BudcomStatus
    exit 0
}

if ($Restart) {
    Write-Step '[3/6] Restart - stop repo-scoped desktop/connector processes'
    Stop-BudcomDesktopProcesses -Reason 'restart'
    $Launch = $true
}

if ($Build) {
    Invoke-BudcomBuild
}
elseif ($Launch -and -not (Test-Path $MainOutput)) {
    Write-Fail "Build output missing. Re-run with -Build or run -Build -Launch together."
}

if ($Launch) {
    Invoke-BudcomLaunch
}

if ($Verify) {
    Invoke-BudcomVerify
}
elseif ($Launch) {
    Write-Step '[5/6] Verify skipped (pass -Verify to validate startup)'
    Write-Step '[6/6] Result - launch issued (detached)'
}

exit 0
