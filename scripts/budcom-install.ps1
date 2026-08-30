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
# Installing the real NSIS package (perMachine: true) always requires a genuine,
# interactive Windows UAC elevation this script cannot click through on its own --
# -DesktopInstall alone only reports what WOULD happen (current vs. installed version);
# actually starting the installer (so the real elevation + wizard appear on screen)
# requires the separate, explicit -DesktopInstallConfirm opt-in, matching the same
# "visible/impactful action needs its own flag" pattern as -DesktopLaunch.
#
# Usage:
#   .\scripts\budcom-install.ps1 -Doctor
#   .\scripts\budcom-install.ps1 -DesktopOnly -Build
#   .\scripts\budcom-install.ps1 -DesktopOnly -DesktopInstall                          # dry run: reports current vs. installed version
#   .\scripts\budcom-install.ps1 -DesktopOnly -DesktopInstall -DesktopInstallConfirm   # actually launches the installer (real UAC prompt)
#   .\scripts\budcom-install.ps1 -DesktopOnly -DesktopInstalledVerify                  # read-only checks against whatever is currently installed
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
    [switch]$DesktopLaunch,           # opt-in: allows the Desktop portion to stop/relaunch the live instance
    [switch]$DesktopInstall,          # dry run by default: compares the newest built installer's version against what's installed
    [switch]$DesktopInstallConfirm,   # opt-in: actually starts the installer (real UAC prompt), only meaningful with -DesktopInstall
    [switch]$DesktopInstalledVerify,  # read-only: inspects whatever is currently installed, does not launch/stop anything
    [string]$DeviceSerial = '',
    [ValidateSet('ProdDebug', 'DevDebug', 'ProdRelease', 'DevRelease')]
    [string]$Variant = 'DevDebug'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$results = @()

function Add-Result {
    param(
        [string]$Stage,
        [ValidateSet('PASS', 'FAIL', 'SKIPPED', 'USER_OS_APPROVAL_REQUIRED', 'PHYSICAL_DEVICE_LOCKED', 'ENVIRONMENT_MISSING')]
        [string]$Status,
        [string]$Detail = ''
    )
    $script:results += [pscustomobject]@{ Stage = $Stage; Status = $Status; Detail = $Detail }
    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        'USER_OS_APPROVAL_REQUIRED' { 'Cyan' }
        'PHYSICAL_DEVICE_LOCKED' { 'Magenta' }
        'ENVIRONMENT_MISSING' { 'Yellow' }
        default { 'Yellow' }
    }
    Write-Host "[$Status] $Stage $(if ($Detail) { "- $Detail" })" -ForegroundColor $color
}

function Get-LatestDesktopInstaller {
    $artifactsRoot = Join-Path $RepoRoot 'release\controlled-pilot'
    if (-not (Test-Path $artifactsRoot)) { return $null }
    Get-ChildItem -Path $artifactsRoot -Recurse -Filter 'BudcomDesktop-*-x64-setup.exe' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Get-InstalledDesktopVersion {
    # Read-only registry lookup -- never launches, stops, or modifies the installed app.
    # electron-builder's NSIS DisplayName embeds the version ("Budcom Desktop 0.4.20"), so match by
    # prefix and read DisplayVersion separately. perMachine:true normally registers under HKLM, but a
    # prior install that ran without elevation can leave an HKCU-only entry -- check both, and both
    # WOW6432Node/native views. InstallLocation is sometimes blank; fall back to UninstallString's
    # directory. -ErrorAction SilentlyContinue on Get-ItemProperty doesn't cover strict-mode property
    # access on registry values that don't have every key, so probe with PSObject.Properties first.
    $roots = @(
        'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    foreach ($root in $roots) {
        $candidates = Get-ItemProperty -Path $root -ErrorAction SilentlyContinue
        foreach ($entry in $candidates) {
            $hasDisplayName = $entry.PSObject.Properties.Name -contains 'DisplayName'
            if (-not $hasDisplayName -or $entry.DisplayName -notlike 'Budcom Desktop*') { continue }
            $installLocation = if ($entry.PSObject.Properties.Name -contains 'InstallLocation') { $entry.InstallLocation } else { $null }
            if (-not $installLocation -and ($entry.PSObject.Properties.Name -contains 'UninstallString')) {
                # electron-builder's NSIS UninstallString carries a trailing argument after the quoted
                # path (e.g. `"...\Uninstall Budcom Desktop.exe" /allusers`) -- extract only the quoted
                # portion, not a bare trailing-quote strip (which leaves the argument attached).
                $quotedMatch = [regex]::Match($entry.UninstallString, '^"([^"]+)"')
                $uninstallExe = if ($quotedMatch.Success) { $quotedMatch.Groups[1].Value } else { $entry.UninstallString.Trim() }
                if ($uninstallExe) { $installLocation = Split-Path -Path $uninstallExe -Parent }
            }
            return [pscustomobject]@{ Version = $entry.DisplayVersion; InstallLocation = $installLocation }
        }
    }
    return $null
}

function Invoke-DesktopInstallStage {
    $installer = Get-LatestDesktopInstaller
    if (-not $installer) {
        Add-Result 'Desktop Install' 'ENVIRONMENT_MISSING' 'no built installer found under release\controlled-pilot\*\artifacts -- run -DesktopOnly -Build (dist:win) first'
        return
    }
    $versionMatch = [regex]::Match($installer.Name, 'BudcomDesktop-(?<v>[\d.]+)-x64-setup\.exe')
    $installerVersion = if ($versionMatch.Success) { $versionMatch.Groups['v'].Value } else { $null }
    $installed = Get-InstalledDesktopVersion

    # The version string alone is not sufficient: it comes from package.json and is only bumped
    # deliberately, so an installed build can be genuinely stale (different code) while still
    # reporting the same version number as a freshly built installer (reproduced live: an Aug-24
    # install and an Aug-30 rebuild both read "0.4.20"). Cross-check against the installed files'
    # own mtime vs. the installer's -- an installed exe strictly older than the built installer
    # means the installer has not actually been applied yet, version string notwithstanding.
    # A small tolerance is required even for a genuinely fresh install: electron-builder finalizes the
    # installer .exe wrapper (blockmap, etc.) a few seconds *after* packing the individual files it
    # embeds, so the embedded exe's own preserved mtime is always slightly *older* than the installer
    # file's mtime, even seconds after a perfect install (reproduced live: 8 seconds older on a
    # confirmed-successful run). 15 minutes comfortably covers that gap without masking a genuinely
    # stale (hours/days old) install.
    $installedIsCurrent = $false
    if ($installed -and $installerVersion -and $installed.Version -eq $installerVersion -and $installed.InstallLocation) {
        $installedExe = Join-Path $installed.InstallLocation 'Budcom Desktop.exe'
        if (Test-Path $installedExe) {
            $installedMtime = (Get-Item $installedExe).LastWriteTime
            $installedIsCurrent = $installedMtime -ge $installer.LastWriteTime.AddMinutes(-15)
        }
    }

    if ($installedIsCurrent) {
        Add-Result 'Desktop Install' 'PASS' "already installed at $($installed.Version) ($($installed.InstallLocation)), installed files are not older than the built installer -- nothing to do"
        return
    }

    $fromLabel = if ($installed) { "$($installed.Version) (installed files dated $((Get-Item (Join-Path $installed.InstallLocation 'Budcom Desktop.exe') -ErrorAction SilentlyContinue).LastWriteTime))" } else { '(not installed)' }
    if (-not $DesktopInstallConfirm) {
        Add-Result 'Desktop Install' 'USER_OS_APPROVAL_REQUIRED' "installer $($installer.Name) ($fromLabel -> $installerVersion) not yet run -- re-run with -DesktopInstallConfirm to launch it (will prompt for a real Windows UAC elevation this script cannot approve)"
        return
    }

    Write-Host "Launching installer -- a real Windows UAC elevation prompt will appear; approve it and complete the wizard." -ForegroundColor Cyan
    Start-Process -FilePath $installer.FullName | Out-Null
    Add-Result 'Desktop Install' 'USER_OS_APPROVAL_REQUIRED' "installer launched ($($installer.FullName)) -- approve the UAC prompt and complete the wizard, then re-run with -DesktopInstalledVerify"
}

function Invoke-DesktopInstalledVerifyStage {
    $installed = Get-InstalledDesktopVersion
    if (-not $installed -or -not $installed.InstallLocation) {
        Add-Result 'Desktop Installed Verify' 'ENVIRONMENT_MISSING' 'Budcom Desktop is not registered as installed'
        return
    }
    $exePath = Join-Path $installed.InstallLocation 'Budcom Desktop.exe'
    if (-not (Test-Path $exePath)) {
        Add-Result 'Desktop Installed Verify' 'FAIL' "registered install location $($installed.InstallLocation) has no Budcom Desktop.exe"
        return
    }
    $procs = @(Get-Process -Name 'Budcom Desktop' -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exePath })
    # desktop-config.json holds Connector/runtime settings, not the selected company -- that lives
    # behind the Connector's own session-status API/logs (see budcom-desktop.ps1 -Status), not a
    # static file this read-only check can safely parse. Only report config presence/validity here.
    $configPath = Join-Path $env:APPDATA '@budcom\desktop\desktop-config.json'
    $configInfo = 'no persisted config found'
    if (Test-Path $configPath) {
        try {
            Get-Content $configPath -Raw | ConvertFrom-Json | Out-Null
            $configInfo = 'config present and valid JSON'
        } catch { $configInfo = "config present but unparsable: $($_.Exception.Message)" }
    }
    Add-Result 'Desktop Installed Verify' 'PASS' "version=$($installed.Version) processes=$($procs.Count) $configInfo -- check company/pairing state via -DesktopOnly -Verify or a screenshot"
}

function Test-AndroidDeviceLocked {
    param([string]$Serial)
    $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { return $null }
    # Best-effort, read-only heuristic (Android's lock-state reporting isn't fully uniform across
    # OEM/versions): a Keyguard-owned top window is a strong signal the device is at its lock screen.
    $dump = & $adb -s $Serial shell dumpsys window 2>$null | Select-String 'mCurrentFocus'
    if ($dump -match 'Keyguard') { return $true }
    return $false
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
    if ($DesktopInstall) { Invoke-DesktopInstallStage }
    if ($DesktopInstalledVerify) { Invoke-DesktopInstalledVerifyStage }
}
else {
    Add-Result 'Desktop' 'SKIPPED' '-AndroidOnly'
}

if ($doAndroid) {
    if (-not $DeviceSerial) {
        Add-Result 'Android' 'SKIPPED' 'no -DeviceSerial provided'
    }
    else {
        $deviceLocked = if ($Install -or $Launch) { Test-AndroidDeviceLocked -Serial $DeviceSerial } else { $false }
        if ($deviceLocked) {
            Add-Result "Android ($Variant @ $DeviceSerial)" 'PHYSICAL_DEVICE_LOCKED' 'device appears to be at its lock screen -- unlock it (PIN/biometric) and re-run; this tool never bypasses a device lock'
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
}
else {
    Add-Result 'Android' 'SKIPPED' '-DesktopOnly'
}

Write-Host ""
Write-Host "=== Evidence Summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-Host

$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
$needsAttention = @($results | Where-Object { $_.Status -in @('USER_OS_APPROVAL_REQUIRED', 'PHYSICAL_DEVICE_LOCKED', 'ENVIRONMENT_MISSING') })
if ($failed.Count -gt 0) {
    Write-Host "FAIL: $($failed.Count) stage(s) failed." -ForegroundColor Red
    exit 1
}
if ($needsAttention.Count -gt 0) {
    Write-Host "NEEDS ATTENTION: $($needsAttention.Count) stage(s) require a human action (see statuses above) -- not a code failure." -ForegroundColor Cyan
    exit 2
}
Write-Host "PASS: all requested stages completed." -ForegroundColor Green
exit 0
