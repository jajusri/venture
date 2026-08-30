# BUDCOM combined operator flow. Orchestrates the already-proven platform-specific
# tools (budcom-doctor.ps1, budcom-desktop.ps1, budcom-android.ps1) -- does not
# duplicate their logic.
#
# SAFETY DEFAULT: the Desktop portion never touches the user's live, currently-running
# Desktop/Connector session -- it only builds and reports status. budcom-desktop.ps1's
# own -Launch/-Restart modes stop any currently-running BUDCOM desktop/connector
# process first, which would interrupt a real, in-progress user session; this wrapper
# only calls that when -DesktopLaunch is explicitly passed, opt-in, off by default.
#
# Usage:
#   .\scripts\budcom-install.ps1 -Doctor
#   .\scripts\budcom-install.ps1 -DesktopOnly -Build
#   .\scripts\budcom-install.ps1 -AndroidOnly -Build -Install -Launch -Verify -DeviceSerial <SERIAL> -Variant DevDebug
#   .\scripts\budcom-install.ps1 -Build -Install -Launch -Verify -DeviceSerial <SERIAL>   # both platforms

[CmdletBinding()]
param(
    [switch]$Doctor,
    [switch]$DesktopOnly,
    [switch]$AndroidOnly,
    [switch]$Build,
    [switch]$Install,
    [switch]$Launch,
    [switch]$Verify,
    [switch]$DesktopLaunch,   # opt-in: allows the Desktop portion to stop/relaunch the live instance
    [string]$DeviceSerial = '',
    [ValidateSet('ProdDebug', 'DevDebug', 'ProdRelease', 'DevRelease')]
    [string]$Variant = 'DevDebug'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$results = @()

function Add-Result {
    param([string]$Stage, [ValidateSet('PASS', 'FAIL', 'SKIPPED')][string]$Status, [string]$Detail = '')
    $script:results += [pscustomobject]@{ Stage = $Stage; Status = $Status; Detail = $Detail }
    $color = switch ($Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
    Write-Host "[$Status] $Stage $(if ($Detail) { "- $Detail" })" -ForegroundColor $color
}

function Invoke-Stage {
    param([string]$Stage, [scriptblock]$Action)
    Write-Host ""
    Write-Host "=== $Stage ===" -ForegroundColor Cyan
    try {
        & $Action
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
            Add-Result $Stage 'FAIL' "exit code $LASTEXITCODE"
        }
        else {
            Add-Result $Stage 'PASS'
        }
    }
    catch {
        Add-Result $Stage 'FAIL' $_.Exception.Message
    }
}

$doAndroid = -not $DesktopOnly
$doDesktop = -not $AndroidOnly

if ($Doctor) {
    Invoke-Stage 'Doctor' { & (Join-Path $RepoRoot 'scripts\budcom-doctor.ps1') }
}

if ($doDesktop) {
    if ($Build) {
        Invoke-Stage 'Desktop Build' { & (Join-Path $RepoRoot 'scripts\budcom-desktop.ps1') -Build }
    }
    if ($DesktopLaunch -and $Launch) {
        Invoke-Stage 'Desktop Launch+Verify (opt-in, stops live instance first)' {
            & (Join-Path $RepoRoot 'scripts\budcom-desktop.ps1') -Launch -Verify
        }
    }
    elseif ($Launch) {
        Add-Result 'Desktop Launch' 'SKIPPED' 'pass -DesktopLaunch to explicitly allow stopping the live running instance'
    }
    if (-not $Build -and -not ($DesktopLaunch -and $Launch)) {
        Invoke-Stage 'Desktop Status (read-only)' { & (Join-Path $RepoRoot 'scripts\budcom-desktop.ps1') -Status }
    }
}
else {
    Add-Result 'Desktop' 'SKIPPED' '-AndroidOnly'
}

if ($doAndroid) {
    if (-not $DeviceSerial) {
        Add-Result 'Android' 'SKIPPED' 'no -DeviceSerial provided'
    }
    else {
        # Must be a hashtable, not an array: array-splatting (@array) passes each element as a
        # positional argument, so literal '-DeviceSerial'/'-Variant' marker strings would bind to
        # budcom-android.ps1's params positionally instead of by name (reproduced live: the device
        # serial ended up bound to -Variant and failed its ValidateSet). Hashtable splatting (@hash)
        # correctly maps each key to its named parameter, including switches via boolean values.
        $androidArgs = @{ DeviceSerial = $DeviceSerial; Variant = $Variant }
        if ($Build) { $androidArgs['Build'] = $true }
        if ($Install) { $androidArgs['Install'] = $true }
        if ($Launch) { $androidArgs['Launch'] = $true }
        if ($Verify) { $androidArgs['Verify'] = $true }
        if (-not ($Build -or $Install -or $Launch -or $Verify)) { $androidArgs['Status'] = $true }
        Invoke-Stage "Android ($Variant @ $DeviceSerial)" {
            & (Join-Path $RepoRoot 'scripts\budcom-android.ps1') @androidArgs
        }
    }
}
else {
    Add-Result 'Android' 'SKIPPED' '-DesktopOnly'
}

Write-Host ""
Write-Host "=== Evidence Summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-Host

$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
if ($failed.Count -gt 0) {
    Write-Host "FAIL: $($failed.Count) stage(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host "PASS: all requested stages completed." -ForegroundColor Green
exit 0
