# Android physical buying-cycle validation harness (Device A)
# Read-only operator helpers — does not modify app data, security settings, or business records.

param(
    [string]$PackageName = "com.budcom.android.debug",
    [string]$EvidenceRoot = "evidence/physical-buying-cycle-$(Get-Date -Format 'yyyy-MM-dd')",
    [string]$DeviceSerial = "",
    [switch]$Install,
    [switch]$Launch,
    [switch]$Screenshot,
    [string]$ScreenshotName = "",
    [switch]$CaptureLog,
    [switch]$StatusOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"

if (-not (Test-Path $Adb)) {
    Write-Error "adb not found at $Adb"
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    $base = @()
    if ($DeviceSerial) { $base += "-s", $DeviceSerial }
    & $Adb @base @Args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Args -join ' ')" }
}

function Get-ConnectedDevices {
    $lines = & $Adb devices -l | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" }
    return $lines | ForEach-Object {
        $parts = $_ -split "\s+"
        [pscustomobject]@{ Serial = $parts[0]; State = $parts[1]; Detail = ($_ -replace "^\S+\s+\S+\s*", "") }
    }
}

Write-Host "=== BUDCOM Android physical validation harness ==="
Write-Host "Repo: $RepoRoot"
Write-Host "ADB:  $Adb"
Write-Host ""

$devices = Get-ConnectedDevices
if ($devices.Count -eq 0) {
    Write-Warning "No ADB devices connected."
} else {
    Write-Host "Connected devices:"
    $devices | Format-Table -AutoSize | Out-String | Write-Host
}

if ($StatusOnly) {
    if ($devices.Count -ge 1 -and -not $DeviceSerial) { $DeviceSerial = $devices[0].Serial }
    if ($DeviceSerial) {
        Write-Host "Device model: $(Invoke-Adb shell getprop ro.product.model)"
        Write-Host "Android:      $(Invoke-Adb shell getprop ro.build.version.release)"
        Write-Host "USB debug:    $(Invoke-Adb shell settings get global adb_enabled)"
        $version = Invoke-Adb shell dumpsys package $PackageName | Select-String "versionName="
        if ($version) { Write-Host "Package:      $PackageName $($version.Line.Trim())" }
        else { Write-Warning "Package $PackageName not installed on $DeviceSerial" }
    }
    exit 0
}

if (-not $DeviceSerial -and $devices.Count -eq 1) { $DeviceSerial = $devices[0].Serial }
if (-not $DeviceSerial) {
    Write-Error "Specify -DeviceSerial or connect exactly one device."
}

New-Item -ItemType Directory -Force -Path (Join-Path $RepoRoot $EvidenceRoot) | Out-Null

if ($Install) {
    Write-Host "Installing devDebug APK (no data wipe)..."
    Push-Location (Join-Path $RepoRoot "apps\budcom_android")
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    .\gradlew.bat :app:installDevDebug
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Gradle install failed" }
    Pop-Location
}

if ($Launch) {
    Write-Host "Launching $PackageName..."
    Invoke-Adb shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
}

if ($Screenshot) {
    if (-not $ScreenshotName) { $ScreenshotName = (Get-Date -Format "HHmmss") }
    $remote = "/sdcard/budcom-evidence-$ScreenshotName.png"
    $local = Join-Path $RepoRoot (Join-Path $EvidenceRoot "$ScreenshotName.png")
    Invoke-Adb shell screencap -p $remote
    Invoke-Adb pull $remote $local | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
    Write-Host "Screenshot: $local"
}

if ($CaptureLog) {
    $logPath = Join-Path $RepoRoot (Join-Path $EvidenceRoot "logcat.txt")
    Invoke-Adb logcat -d | Out-File -FilePath $logPath -Encoding utf8
    Write-Host "Logcat: $logPath"
}

Write-Host "Done."
