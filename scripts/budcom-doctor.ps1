# BUDCOM development environment doctor (read-only).
# Usage: .\scripts\budcom-doctor.ps1

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$AndroidDir = Join-Path $RepoRoot 'apps\budcom_android'
$DesktopDir = Join-Path $RepoRoot 'apps\budcom_desktop'
$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$DesktopMain = Join-Path $DesktopDir 'dist\main\main.js'
$DesktopElectron = Join-Path $DesktopDir 'node_modules\electron\dist\electron.exe'
$ProdApk = Join-Path $AndroidDir 'app\build\outputs\apk\prod\debug\app-prod-debug.apk'

$results = @()

function Add-Check {
    param([string]$Area, [string]$Name, [ValidateSet('PASS', 'WARN', 'FAIL')][string]$Status, [string]$Detail)
    $script:results += [pscustomobject]@{ Area = $Area; Check = $Name; Status = $Status; Detail = $Detail }
}

function Run-GitCheck {
    Push-Location $RepoRoot
    try {
        $head = git rev-parse HEAD 2>$null
        $branch = git branch --show-current 2>$null
        $dirty = [bool](git status --short 2>$null)
        Add-Check 'Git' 'HEAD' 'PASS' "$branch @ $head"
        Add-Check 'Git' 'Worktree' $(if ($dirty) { 'WARN' } else { 'PASS' }) $(if ($dirty) { 'dirty' } else { 'clean' })
    }
    catch {
        Add-Check 'Git' 'Repository' 'FAIL' $_.Exception.Message
    }
    finally { Pop-Location }
}

function Run-DesktopCheck {
    if (-not (Test-Path $DesktopDir)) {
        Add-Check 'Desktop' 'Project' 'FAIL' "Missing $DesktopDir"
        return
    }
    Add-Check 'Desktop' 'Project' 'PASS' $DesktopDir
    Add-Check 'Desktop' 'Electron' $(if (Test-Path $DesktopElectron) { 'PASS' } else { 'WARN' }) $DesktopElectron
    Add-Check 'Desktop' 'Build output' $(if (Test-Path $DesktopMain) { 'PASS' } else { 'WARN' }) $DesktopMain
    if (Test-Path $DesktopMain) {
        Add-Check 'Desktop' 'Build mtime' 'PASS' ((Get-Item $DesktopMain).LastWriteTime.ToString('s'))
    }
    $port8080 = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($port8080) {
        $owner = Get-Process -Id $port8080.OwningProcess -ErrorAction SilentlyContinue
        Add-Check 'Desktop' 'Port 8080' 'PASS' "LISTEN pid=$($port8080.OwningProcess) $($owner.ProcessName)"
    }
    else {
        Add-Check 'Desktop' 'Port 8080' 'WARN' 'not listening'
    }
}

function Run-AndroidCheck {
    if (-not (Test-Path $AndroidDir)) {
        Add-Check 'Android' 'Project' 'FAIL' "Missing $AndroidDir"
        return
    }
    Add-Check 'Android' 'Project' 'PASS' $AndroidDir
    Add-Check 'Android' 'Gradle wrapper' $(if (Test-Path (Join-Path $AndroidDir 'gradlew.bat')) { 'PASS' } else { 'FAIL' }) 'gradlew.bat'
    Add-Check 'Android' 'JDK' $(if (Test-Path $JavaHome) { 'PASS' } else { 'FAIL' }) $JavaHome
    Add-Check 'Android' 'ADB' $(if (Test-Path $Adb) { 'PASS' } else { 'FAIL' }) $Adb
    if (Test-Path $ProdApk) {
        Add-Check 'Android' 'ProdDebug APK' 'PASS' "$ProdApk $((Get-Item $ProdApk).LastWriteTime)"
    }
    else {
        Add-Check 'Android' 'ProdDebug APK' 'WARN' 'not built yet'
    }
    if (Test-Path $Adb) {
        $lines = & $Adb devices -l 2>$null | Select-Object -Skip 1 | Where-Object { $_.Trim() }
        $count = @($lines).Count
        Add-Check 'Android' 'ADB devices' $(if ($count -gt 0) { 'PASS' } else { 'WARN' }) "$count connected"
        foreach ($line in $lines) {
            Add-Check 'Android' 'Device' 'PASS' $line.Trim()
        }
    }
}

function Run-NodeCheck {
    try {
        $nodeV = (node -v 2>$null)
        Add-Check 'Node' 'node' $(if ($nodeV) { 'PASS' } else { 'WARN' }) $nodeV
    }
    catch { Add-Check 'Node' 'node' 'WARN' 'not found in PATH' }
    try {
        $npmV = (npm -v 2>$null)
        Add-Check 'Node' 'npm' $(if ($npmV) { 'PASS' } else { 'WARN' }) $npmV
    }
    catch { Add-Check 'Node' 'npm' 'WARN' 'not found in PATH' }
}

function Run-PortCheck {
    foreach ($port in @(8080, 8081, 8443, 9000, 5432)) {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) {
            $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            Add-Check 'Ports' "Port $port" 'PASS' "LISTEN $($proc.ProcessName) pid=$($conn.OwningProcess)"
        }
        else {
            Add-Check 'Ports' "Port $port" 'WARN' 'not listening'
        }
    }
}

Write-Host '=== BUDCOM Doctor (read-only) ==='
Run-GitCheck
Run-NodeCheck
Run-DesktopCheck
Run-AndroidCheck
Run-PortCheck

$results | Format-Table -AutoSize | Out-String | Write-Host

$fail = @($results | Where-Object Status -eq 'FAIL').Count
$warn = @($results | Where-Object Status -eq 'WARN').Count
if ($fail -gt 0) {
    Write-Host "FAIL: $fail check(s) failed, $warn warning(s)" -ForegroundColor Red
    exit 1
}
if ($warn -gt 0) {
    Write-Host "WARN: $warn warning(s), no hard failures" -ForegroundColor Yellow
    exit 0
}
Write-Host 'PASS: environment looks ready' -ForegroundColor Green
exit 0
