# Canonical BUDCOM Android developer deploy/status tool.
# Usage:
#   .\scripts\budcom-android.ps1 -Status -DeviceSerial <SERIAL>
#   .\scripts\budcom-android.ps1 -DeviceSerial <SERIAL> -Variant ProdDebug -Build -Install -Launch -Verify

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$DeviceSerial = '',

    [ValidateSet('ProdDebug', 'DevDebug', 'ProdRelease', 'DevRelease')]
    [string]$Variant = 'ProdDebug',

    [switch]$Status,
    [switch]$Build,
    [switch]$Install,
    [switch]$Launch,
    [switch]$Verify
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Script:AndroidDir = Join-Path $RepoRoot 'apps\budcom_android'
$Script:JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$Script:SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$Script:Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$Script:LauncherLogDir = Join-Path $env:TEMP 'budcom-android-launcher'
$Script:BuildTimeoutSec = 900
$Script:StartupTimeoutSec = 45
$Script:VerifyAliveSec = 4
$Script:HeartbeatSec = 20
$Script:LauncherLogFile = $null

function Initialize-LauncherLog {
    New-Item -ItemType Directory -Force -Path $LauncherLogDir | Out-Null
    $Script:LauncherLogFile = Join-Path $LauncherLogDir ("android-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))
}

function Write-Log {
    param([string]$Message)
    if ($LauncherLogFile) {
        Add-Content -Path $LauncherLogFile -Value ("[{0:yyyy-MM-dd HH:mm:ss}] {1}" -f (Get-Date), $Message) -Encoding UTF8
    }
}

function Write-Step {
    param([string]$Message)
    Write-Host $Message
    Write-Log $Message
}

function Write-Pass {
    param([string]$Message = 'All requested operations completed successfully.')
    Write-Host "PASS: $Message" -ForegroundColor Green
    Write-Log "PASS: $Message"
    exit 0
}

function Write-Fail {
    param([string]$Message)
    Write-Host "FAIL: $Message" -ForegroundColor Red
    Write-Log "FAIL: $Message"
    if ($LauncherLogFile) { Write-Host "Launcher log: $LauncherLogFile" }
    exit 1
}

function Get-RepoGitHead {
    Push-Location $RepoRoot
    try { return (git rev-parse HEAD 2>$null) }
    finally { Pop-Location }
}

function Get-RepoGitState {
    Push-Location $RepoRoot
    try {
        return [ordered]@{
            Head = (git rev-parse HEAD 2>$null)
            Dirty = [bool](git status --short 2>$null)
            StatusShort = @(git status --short 2>$null)
        }
    }
    finally { Pop-Location }
}

function Get-LastBuildMetadata {
    $metaPath = Join-Path $LauncherLogDir 'last-build.json'
    if (-not (Test-Path $metaPath)) { return $null }
    try { return Get-Content -Raw -Path $metaPath | ConvertFrom-Json }
    catch { return $null }
}

function Test-BuildArtifactAuthoritative {
    param($Config)
    $apk = Get-ApkMetadata -ApkPath $Config.ApkPath
    if (-not $apk) {
        Write-Fail "APK missing or unreadable at $($Config.ApkPath). Run with -Build."
    }
    if ($apk.Package -ne $Config.Package) {
        Write-Fail "APK package mismatch: expected $($Config.Package), got $($apk.Package)."
    }
    $meta = Get-LastBuildMetadata
    if (-not $meta) {
        Write-Fail "No build metadata for this variant. Run with -Build before -Install."
    }
    if ($meta.variant -ne $Config.Name) {
        Write-Fail "Build metadata variant '$($meta.variant)' does not match requested '$($Config.Name)'."
    }
    if ($meta.package -ne $Config.Package) {
        Write-Fail "Build metadata package '$($meta.package)' does not match variant package '$($Config.Package)'."
    }
    if ($meta.apkPath -ne $Config.ApkPath) {
        Write-Fail "Build metadata APK path does not match expected variant APK path."
    }
    $git = Get-RepoGitState
    if ($meta.head -ne $git.Head) {
        Write-Fail "Built APK is from Git HEAD $($meta.head) but current HEAD is $($git.Head). Re-run with -Build."
    }
    if ($git.Dirty) {
        Write-Fail "Worktree is dirty; refusing install of ambiguous mixed-source APK. Commit or stash product changes, then -Build -Install."
    }
    $builtAt = [datetime]::Parse($meta.builtAt)
    if ($apk.Modified -lt $builtAt.AddSeconds(-2)) {
        Write-Fail "APK on disk is older than the recorded build. Re-run with -Build."
    }
    return $apk
}

function Resolve-VariantConfig {
    param([string]$Name)
    $flavor = if ($Name.StartsWith('Dev')) { 'dev' } else { 'prod' }
    $buildType = if ($Name.EndsWith('Release')) { 'Release' } else { 'Debug' }
    $flavorCap = if ($flavor -eq 'dev') { 'Dev' } else { 'Prod' }
    $taskSuffix = "$flavorCap$buildType"
    $apkDir = Join-Path $AndroidDir "app\build\outputs\apk\$flavor\$($buildType.ToLower())"
    $apkName = "app-$flavor-$($buildType.ToLower()).apk"
    $package = switch ($Name) {
        'ProdDebug' { 'com.budcom.android.debug' }
        'DevDebug' { 'com.budcom.android.dev.debug' }
        'ProdRelease' { 'com.budcom.android' }
        'DevRelease' { 'com.budcom.android.dev' }
        default { throw "Unknown variant $Name" }
    }
    return [ordered]@{
        Name = $Name
        Flavor = $flavor
        BuildType = $buildType
        GradleAssemble = ":app:assemble$taskSuffix"
        GradleInstall = ":app:install$taskSuffix"
        ApkPath = Join-Path $apkDir $apkName
        Package = $package
        LaunchActivity = "$package/com.budcom.android.app.MainActivity"
    }
}

function Get-Aapt {
    $aapt = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Recurse -Filter 'aapt.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $aapt) { return $null }
    return $aapt.FullName
}

function Get-ApkMetadata {
    param([string]$ApkPath)
    $aapt = Get-Aapt
    if (-not $aapt -or -not (Test-Path $ApkPath)) {
        return $null
    }
    $badging = & $aapt dump badging $ApkPath 2>$null
    $meta = [ordered]@{
        Path = $ApkPath
        Modified = (Get-Item $ApkPath).LastWriteTime
        Package = $null
        VersionCode = $null
        VersionName = $null
    }
    foreach ($line in $badging) {
        if ($line -match "^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
            $meta.Package = $Matches[1]
            $meta.VersionCode = [int]$Matches[2]
            $meta.VersionName = $Matches[3]
        }
    }
    return $meta
}

function Get-InstalledPackageInfo {
    param([string]$Serial, [string]$Package)
    $out = & $Adb -s $Serial shell dumpsys package $Package 2>$null
    if (-not $out) { return $null }
    $versionNameLine = ($out | Select-String '^\s+versionName=' | Select-Object -First 1).Line
    $versionCodeLine = ($out | Select-String '^\s+versionCode=' | Select-Object -First 1).Line
    if (-not $versionNameLine) { return $null }
    $vn = if ($versionNameLine -match 'versionName=([^\s]+)') { $Matches[1] } else { $null }
    $vc = if ($versionCodeLine -match 'versionCode=(\d+)') { [int]$Matches[1] } else { 0 }
    return [ordered]@{
        Package = $Package
        VersionName = $vn
        VersionCode = $vc
    }
}

function Get-ConnectedDevices {
    if (-not (Test-Path $Adb)) { return @() }
    $lines = & $Adb devices -l | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }
    return @($lines | ForEach-Object {
        $parts = $_ -split '\s+'
        [pscustomobject]@{ Serial = $parts[0]; State = $parts[1]; Detail = ($_ -replace '^\S+\s+\S+\s*', '') }
    })
}

function Invoke-AdbCommand {
    param(
        [string]$Serial,
        [string[]]$Command
    )
    & $Adb -s $Serial @Command
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "adb failed ($Serial): $($Command -join ' ')"
    }
}

function Require-DeviceSerial {
    $devices = @(Get-ConnectedDevices)
    if ($DeviceSerial) {
        $match = $devices | Where-Object { $_.Serial -eq $DeviceSerial }
        if (-not $match) {
            Write-Fail "Device serial '$DeviceSerial' not connected. Connected: $($devices.Serial -join ', ')"
        }
        return $DeviceSerial
    }
    if ($devices.Count -eq 1) {
        return $devices[0].Serial
    }
    Write-Fail "Multiple devices connected ($($devices.Count)). Pass -DeviceSerial explicitly."
}

function Invoke-Gradle {
    param([string]$Task, [string]$Serial)
    $logPath = Join-Path $LauncherLogDir ("gradle-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/c `"$(Join-Path $AndroidDir 'gradlew.bat')`" --no-daemon --no-parallel --max-workers=1 $Task"
    $psi.WorkingDirectory = $AndroidDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    if (Test-Path $JavaHome) { $psi.Environment['JAVA_HOME'] = $JavaHome }
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $psi.Environment['GRADLE_USER_HOME'] = $gradleHome
    if ($Serial) { $psi.Environment['ANDROID_SERIAL'] = $Serial }

    $started = Get-Date
    $proc = [Diagnostics.Process]::Start($psi)
    $lastBeat = Get-Date
    while (-not $proc.HasExited) {
        $elapsed = (Get-Date) - $started
        if ($elapsed.TotalSeconds -gt $BuildTimeoutSec) {
            try { $proc.Kill() } catch { }
            Write-Fail "Gradle exceeded ${BuildTimeoutSec}s. Log: $logPath"
        }
        if (((Get-Date) - $lastBeat).TotalSeconds -ge $HeartbeatSec) {
            Write-Step ("  gradle still running ({0:N0}s)..." -f $elapsed.TotalSeconds)
            $lastBeat = Get-Date
        }
        Start-Sleep -Milliseconds 500
    }
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    @($stdout, $stderr, "exit=$($proc.ExitCode)", "task=$Task", "head=$(Get-RepoGitHead)", "elapsed=$(((Get-Date)-$started).TotalSeconds)s") |
        Set-Content -Path $logPath -Encoding UTF8
    if ($proc.ExitCode -ne 0) {
        Write-Fail "Gradle failed (exit $($proc.ExitCode)). Log: $logPath"
    }
    Write-Step ("  gradle $Task succeeded in {0:N1}s; log: {1}" -f ((Get-Date)-$started).TotalSeconds, $logPath)
    return $logPath
}

function Show-AndroidStatus {
    param([string]$Serial, $Config)
    $git = Get-RepoGitState
    Write-Step '[7/7] Result - status (read-only)'
    Write-Step "Git HEAD: $($git.Head)"
    Write-Step ("Worktree: {0}" -f $(if ($git.Dirty) { 'DIRTY' } else { 'clean' }))
    if ($git.Dirty) {
        foreach ($line in $git.StatusShort) { Write-Step "  dirty: $line" }
    }
    Write-Step "Android project: $AndroidDir"
    Write-Step "Variant: $($Config.Name) -> package $($Config.Package)"
    Write-Step "ADB: $Adb"
    Write-Step "Device serial: $Serial"
    Write-Step ("Model: {0}" -f (& $Adb -s $Serial shell getprop ro.product.model))
    Write-Step ("Android: {0}" -f (& $Adb -s $Serial shell getprop ro.build.version.release))

    $installed = Get-InstalledPackageInfo -Serial $Serial -Package $Config.Package
    if ($installed) {
        Write-Step ("Installed: $($installed.Package) $($installed.VersionName) (code $($installed.VersionCode))")
    }
    else {
        Write-Step "Installed: package $($Config.Package) not present"
    }

    if (Test-Path $Config.ApkPath) {
        $apk = Get-ApkMetadata -ApkPath $Config.ApkPath
        Write-Step ("Built APK: $($Config.ApkPath)")
        Write-Step ("  modified: $($apk.Modified)")
        Write-Step ("  metadata: $($apk.Package) $($apk.VersionName) (code $($apk.VersionCode))")
        $meta = Get-LastBuildMetadata
        if ($meta) {
            Write-Step ("  last build HEAD: $($meta.head)")
            Write-Step ("  last build at: $($meta.builtAt)")
            Write-Step ("  last build variant: $($meta.variant)")
            if ($meta.head -eq $git.Head -and $meta.variant -eq $Config.Name -and -not $git.Dirty) {
                Write-Step '  APK authority: matches current HEAD and variant'
            }
            else {
                Write-Step '  APK authority: STALE or ambiguous (rebuild required before install)'
            }
        }
        else {
            Write-Step '  APK authority: no last-build.json (rebuild required before install)'
        }
        if ($installed) {
            if ($installed.VersionCode -eq $apk.VersionCode) {
                Write-Step '  installed vs APK: versionCode MATCH'
            }
            elseif ($apk.VersionCode -lt $installed.VersionCode) {
                Write-Step '  installed vs APK: APK would be DOWNGRADE (install refused)'
            }
            else {
                Write-Step '  installed vs APK: APK is newer than installed'
            }
        }
    }
    else {
        Write-Step "Built APK: missing ($($Config.ApkPath))"
    }

    $proc = & $Adb -s $Serial shell pidof $Config.Package 2>$null
    Write-Step ("Process running: {0}" -f $(if ($proc) { "YES (pid $proc)" } else { 'NO' }))
    Write-Pass 'Status collected.'
}

function Invoke-AndroidBuild {
    param($Config)
    Write-Step "[3/7] Build - $($Config.GradleAssemble)"
    if (-not (Test-Path (Join-Path $AndroidDir 'gradlew.bat'))) {
        Write-Fail "Gradle wrapper missing in $AndroidDir"
    }
    Invoke-Gradle -Task $Config.GradleAssemble -Serial '' | Out-Null
    if (-not (Test-Path $Config.ApkPath)) {
        Write-Fail "Build finished but APK missing: $($Config.ApkPath)"
    }
    $apk = Get-ApkMetadata -ApkPath $Config.ApkPath
    if ($apk.Package -ne $Config.Package) {
        Write-Fail "APK package mismatch: expected $($Config.Package), got $($apk.Package)"
    }
    $metaPath = Join-Path $LauncherLogDir 'last-build.json'
    @{
        head = Get-RepoGitHead
        variant = $Config.Name
        package = $apk.Package
        versionCode = $apk.VersionCode
        versionName = $apk.VersionName
        apkPath = $Config.ApkPath
        builtAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -Path $metaPath -Encoding UTF8
    Write-Step "  build metadata: $metaPath"
}

function Invoke-AndroidInstall {
    param([string]$Serial, $Config)
    Write-Step '[5/7] Install - adb install -r (preserve data)'
    $apk = Test-BuildArtifactAuthoritative -Config $Config
    $installed = Get-InstalledPackageInfo -Serial $Serial -Package $Config.Package
    if ($installed -and $apk.VersionCode -lt $installed.VersionCode) {
        Write-Fail "Refusing downgrade: APK code $($apk.VersionCode) < installed $($installed.VersionCode)."
    }
    if ($apk.Package -ne $Config.Package) {
        Write-Fail "APK package $($apk.Package) does not match variant package $($Config.Package)"
    }
    Write-Step ("  installing $($apk.Package) $($apk.VersionName) (code $($apk.VersionCode))")
    & $Adb -s $Serial install -r $Config.ApkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "adb install -r failed for $($Config.ApkPath)"
    }
    $after = Get-InstalledPackageInfo -Serial $Serial -Package $Config.Package
    if (-not $after -or $after.VersionCode -ne $apk.VersionCode) {
        Write-Fail "Install verification failed: device reports $($after.VersionCode), expected $($apk.VersionCode)"
    }
    Write-Step '  install verified on device'
}

function Invoke-AndroidLaunch {
    param([string]$Serial, $Config)
    Write-Step '[5/7] Launch - MainActivity'
    Invoke-AdbCommand -Serial $Serial -Command @('shell', 'am', 'force-stop', $Config.Package)
    Invoke-AdbCommand -Serial $Serial -Command @('shell', 'am', 'start', '-n', $Config.LaunchActivity, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER')
}

function Invoke-AndroidVerify {
    param([string]$Serial, $Config)
    Write-Step '[6/7] Verify - process + bounded logcat'
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSec)
    $appPid = $null
    while ((Get-Date) -lt $deadline) {
        $appPid = Get-ProcessIdForPackage -Serial $Serial -Package $Config.Package
        if ($appPid) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $appPid) {
        $fatal = Get-BoundedFatalLog -Serial $Serial
        $extra = if ($fatal) { $fatal } else { 'No FATAL lines in recent logcat.' }
        Write-Fail "No process for $($Config.Package) within ${StartupTimeoutSec}s. $extra"
    }
    Write-Step "  process alive: PID $appPid"
    Start-Sleep -Seconds $VerifyAliveSec
    $still = Get-ProcessIdForPackage -Serial $Serial -Package $Config.Package
    if (-not $still) {
        $fatal = Get-BoundedFatalLog -Serial $Serial
        Write-Fail "Process exited during observation. $fatal"
    }
    $top = & $Adb -s $Serial shell dumpsys activity activities 2>$null | Select-String 'topResumedActivity' | Select-Object -First 1
    if ($top) { Write-Step "  $top" }
    $fatal = Get-BoundedFatalLog -Serial $Serial
    if ($fatal) {
        Write-Fail "Fatal startup evidence in logcat. $fatal"
    }
}

function Get-ProcessIdForPackage {
    param([string]$Serial, [string]$Package)
    $raw = & $Adb -s $Serial shell pidof $Package 2>$null
    if ($null -eq $raw) { return '' }
    return $raw.ToString().Trim()
}

function Get-BoundedFatalLog {
    param([string]$Serial)
    $lines = @(& $Adb -s $Serial logcat -d -t 40 2>$null | Select-String -Pattern 'FATAL EXCEPTION' | Select-Object -First 2)
    if ($lines.Count -eq 0) { return $null }
    return ('Logcat: ' + ($lines.Line -join ' | '))
}

# --- Main ---
Initialize-LauncherLog

if (-not ($Status -or $Build -or $Install -or $Launch -or $Verify)) {
    Write-Step 'No mode selected. Examples:'
    Write-Step '  .\scripts\budcom-android.ps1 -Status -DeviceSerial <SERIAL>'
    Write-Step '  .\scripts\budcom-android.ps1 -DeviceSerial <SERIAL> -Variant ProdDebug -Build -Install -Launch -Verify'
    exit 0
}

Write-Step '[1/7] Repository'
if (-not (Test-Path $Adb)) {
    Write-Fail "adb not found at $Adb"
}
$devices = @(Get-ConnectedDevices)
Write-Step ("  connected devices: {0}" -f $(if ($devices.Count) { ($devices.Serial -join ', ') } else { '(none)' }))

Write-Step '[2/7] Environment'
$Config = Resolve-VariantConfig -Name $Variant
Write-Step ("  variant $($Config.Name) -> $($Config.Package)")
Write-Step ("  expected APK: $($Config.ApkPath)")
if (Test-Path $JavaHome) { Write-Step "  JAVA_HOME: $JavaHome" } else { Write-Step "WARN: JAVA_HOME path missing: $JavaHome" }

$serial = Require-DeviceSerial

if ($Status -and -not ($Build -or $Install -or $Launch -or $Verify)) {
    Show-AndroidStatus -Serial $serial -Config $Config
}

if ($Build) {
    Invoke-AndroidBuild -Config $Config
}
elseif (($Install -or $Launch -or $Verify) -and -not (Test-Path $Config.ApkPath)) {
    if ($Install) {
        Write-Fail "APK missing at $($Config.ApkPath). Run with -Build."
    }
    Write-Fail "APK missing at $($Config.ApkPath). Run with -Build."
}

if ($Install) {
    Invoke-AndroidInstall -Serial $serial -Config $Config
}

if ($Launch) {
    if (-not $Install) {
        Write-Step '[4/7] Target device confirmed'
    }
    Invoke-AndroidLaunch -Serial $serial -Config $Config
}

if ($Verify) {
    Invoke-AndroidVerify -Serial $serial -Config $Config
}

Write-Step '[7/7] Result'
Write-Pass "Android operations completed for $serial ($Variant)."
