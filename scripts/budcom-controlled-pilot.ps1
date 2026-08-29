# BUDCOM controlled-pilot, two-company / two-phone physical enrollment orchestration.
#
# ORCHESTRATION ONLY -- this script owns no business/security logic of its own. Every authority
# decision (business/membership creation, equivalence/idempotency, enrollment-grant issuance and
# consumption, credential issuance) is made by the certified backend application services via
# `pilot-provision.ts` and Trust's real HTTP endpoints, and by the real, unmodified
# `TrustEnrollmentRepository`/Android Keystore on-device. This script's only job is to call the
# existing tools (`budcom-services.ps1`, `budcom-android.ps1`, `pilot-provision.ts`) in the right
# order and move non-secret bytes between them.
#
# Usage:
#   .\scripts\budcom-controlled-pilot.ps1 -Doctor
#   .\scripts\budcom-controlled-pilot.ps1 -Bootstrap
#   .\scripts\budcom-controlled-pilot.ps1 -Start
#   .\scripts\budcom-controlled-pilot.ps1 -Enroll
#   .\scripts\budcom-controlled-pilot.ps1 -Verify
#   .\scripts\budcom-controlled-pilot.ps1 -Status
#   .\scripts\budcom-controlled-pilot.ps1 -RunTransport
#   .\scripts\budcom-controlled-pilot.ps1 -Stop
#   .\scripts\budcom-controlled-pilot.ps1 -RunAll
#
# NEVER prints a grant secret, a credential body, a private key, or a database connection string.

[CmdletBinding()]
param(
    [switch]$Doctor,
    [switch]$Bootstrap,
    [switch]$Start,
    [switch]$Enroll,
    [switch]$Verify,
    [switch]$Status,
    [switch]$Stop,
    [switch]$RunTransport,
    [switch]$RunAll,

    [string]$PhoneASerial = '',
    [string]$PhoneBSerial = ''
)

Set-StrictMode -Version Latest
# Deliberately 'Continue', not 'Stop': every native command this script calls (adb, npm) is checked
# via an explicit $LASTEXITCODE test immediately afterward (never relied on PowerShell's own
# exception machinery for control flow) -- but under 'Stop', ANY stderr line from a native command
# becomes a terminating NativeCommandError even on a genuine success (reproduced live: adb push's own
# progress line, and adb shell am broadcast's own confirmation line, both write to stderr and both
# aborted this entire script despite exit code 0). 'Continue' lets those informational lines print
# and this script's own explicit exit-code checks remain the sole source of truth for failure.
$ErrorActionPreference = 'Continue'

$Script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Script:BackendDir = Join-Path $RepoRoot 'backend'
$Script:SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$Script:Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$Script:DevDebugPackage = 'com.budcom.android.dev.debug'
$Script:PilotReceiver = "$DevDebugPackage/com.budcom.android.pilotharness.PilotEnrollmentReceiver"
$Script:PilotAction = "$DevDebugPackage.action.PILOT_ENROLL"
$Script:PayloadPath = "/data/data/$DevDebugPackage/files/pilot_enrollment.json"
$Script:ResultPath = "/data/data/$DevDebugPackage/files/pilot_enrollment_result.json"
$Script:TransportReceiver = "$DevDebugPackage/com.budcom.android.pilotharness.PilotTransportReceiver"
$Script:TransportAction = "$DevDebugPackage.action.PILOT_TRANSPORT"
$Script:TransportPayloadPath = "/data/data/$DevDebugPackage/files/pilot_transport.json"
$Script:TransportResultPath = "/data/data/$DevDebugPackage/files/pilot_transport_result.json"
$Script:TrustPort = 8080
$Script:RelayPort = 8082
$Script:FullScope = 'manage_memberships,approve_memberships,register_devices,revoke_devices,issue_credentials,send_orders,confirm_orders,receive_orders'

function Import-DotEnv {
    $envFile = Join-Path $BackendDir '.env'
    if (-not (Test-Path $envFile)) {
        Write-Warn "backend\.env not found -- copy backend\.env.example to backend\.env and set BUDCOM_TRUST_DATABASE_URL first."
        return
    }
    Get-Content $envFile | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1])
    }
}

$Script:TestCompanies = @(
    [ordered]@{ Index = 1; Slug = 'pilot-test-1'; DisplayName = 'BUDCOM Test 1 Company'; Actor = 'pilot-test-1-actor'; VerificationId = 'pilot-test-1-verified' },
    [ordered]@{ Index = 2; Slug = 'pilot-test-2'; DisplayName = 'BUDCOM Test 2 Company'; Actor = 'pilot-test-2-actor'; VerificationId = 'pilot-test-2-verified' }
)

function Get-HostTrustPort {
    # Where Trust is actually listening on the HOST right now -- normally == $TrustPort, but may be
    # session-overridden (BUDCOM_TRUST_PORT in backend/.env) to avoid colliding with an unrelated
    # already-running local service on the default port. The DEVICE side of every `adb reverse` below
    # always stays $TrustPort (the address every enrolled phone actually holds), only the HOST side
    # follows the override -- a port-translating tunnel, never a re-enrollment.
    if ($env:BUDCOM_TRUST_PORT) { return $env:BUDCOM_TRUST_PORT }
    return $TrustPort
}

function Get-SafeProperty {
    # PilotTransportResult fields left at their (null) default are omitted from the JSON entirely --
    # kotlinx.serialization's default encodeDefaults=false -- so an exception/failure outcome (e.g.
    # the deliberate duplicate-key exception this script's own idempotency proof expects) genuinely
    # lacks keys like orderId/transportState/transportError. Under Set-StrictMode -Version Latest,
    # plain `$result.orderId` throws PropertyNotFoundException for a genuinely-absent key (unlike a
    # present key with a null value, which is fine) -- this accessor makes that safe everywhere this
    # script reads an optional PilotTransportResult field.
    param($Object, [string]$Name)
    $prop = $Object.PSObject.Properties[$Name]
    if ($prop) { return $prop.Value }
    return $null
}

function Write-Section { param([string]$Message) Write-Host "`n=== $Message ===" -ForegroundColor Cyan }
function Write-Ok { param([string]$Message) Write-Host "PASS: $Message" -ForegroundColor Green }
function Write-Warn { param([string]$Message) Write-Host "WARN: $Message" -ForegroundColor Yellow }
function Write-ErrorAndExit { param([string]$Message) Write-Host "FAIL: $Message" -ForegroundColor Red; exit 1 }

# --- backend CLI invocation (never echoes secret-bearing stdout to the console) ---

function Invoke-BackendCli {
    param([string]$NpmScript, [string[]]$CliArgs, [switch]$AllowFailure)
    Push-Location $BackendDir
    try {
        $output = & npm run $NpmScript -- @CliArgs 2>&1
        $exit = $LASTEXITCODE
        if ($exit -ne 0 -and -not $AllowFailure) {
            Write-ErrorAndExit "pilot-provision '$($CliArgs -join ' ')' failed (exit $exit). Output: $($output -join "`n")"
        }
        # npm's own banner lines ("> @budcom/backend...", "> tsx ...") precede the JSON payload --
        # take everything from the first '{' onward.
        $joined = ($output -join "`n")
        $jsonStart = $joined.IndexOf('{')
        if ($jsonStart -lt 0) { return $null }
        try { return ($joined.Substring($jsonStart) | ConvertFrom-Json) } catch { return $null }
    } finally {
        Pop-Location
    }
}

function Get-OrCreateTestBusiness {
    param($Company)
    $result = Invoke-BackendCli -NpmScript 'pilot:provision' -CliArgs @('create-business', '--actor', $Company.Actor, '--verification-id', $Company.VerificationId, '--name', $Company.DisplayName, '--intent', "$($Company.Slug)-create")
    if (-not $result) { Write-ErrorAndExit "create-business returned no parseable result for $($Company.DisplayName)" }
    return $result
}

function Set-TestBusinessScope {
    param($Company, $Created)
    $currentScope = @($Created.authorityScope) | Sort-Object
    $targetScope = @($FullScope -split ',') | Sort-Object
    if (-not (Compare-Object $currentScope $targetScope)) {
        Write-Host "  [$($Company.DisplayName)] scope already correct -- no change (avoids an unnecessary authority-epoch bump)."
        return
    }
    Write-Host "  [$($Company.DisplayName)] scope differs from target -- updating."
    Invoke-BackendCli -NpmScript 'pilot:provision' -CliArgs @('grant-scope', '--membership', $Created.membershipId, '--scope', $FullScope) | Out-Null
}

# --- ADB helpers ---

function Get-ConnectedDevices {
    if (-not (Test-Path $Adb)) { return @() }
    $lines = & $Adb devices -l | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }
    return @($lines | ForEach-Object { ($_ -split '\s+')[0] })
}

function Resolve-PhoneSerials {
    $devices = @(Get-ConnectedDevices) | Sort-Object
    $a = if ($PhoneASerial) { $PhoneASerial } elseif ($devices.Count -ge 1) { $devices[0] } else { $null }
    $b = if ($PhoneBSerial) { $PhoneBSerial } elseif ($devices.Count -ge 2) { $devices[1] } else { $null }
    if (-not $a -or -not $b -or $a -eq $b) {
        Write-ErrorAndExit "Need exactly two distinct connected ADB devices (found: $($devices -join ', ')). Pass -PhoneASerial/-PhoneBSerial explicitly if more than two are attached."
    }
    return [ordered]@{ A = $a; B = $b }
}

function Invoke-PilotHarnessOp {
    param([string]$Serial, [hashtable]$Payload, [string]$PayloadPathOnDevice, [string]$ResultPathOnDevice, [string]$ReceiverComponent, [string]$BroadcastAction, [string]$FailureLabel)
    # -Depth 10: the default depth (2) truncates nested payloads like a credential object embedded
    # in the outer hashtable (hashtable -> PSCustomObject -> its own list/scalar properties already
    # exceeds 2 levels), silently degrading nested values to their string representation instead of
    # real JSON -- always pass an explicit depth generous enough for any payload this script builds.
    $json = $Payload | ConvertTo-Json -Compress -Depth 10
    $sharedTempPathOnDevice = "/data/local/tmp/pilot_op_$Serial.json"
    $localTempFile = [System.IO.Path]::GetTempFileName()
    try {
        # `adb shell run-as <pkg> sh -c 'cmd > file'` cannot be built from separate PowerShell/adb
        # argv elements: adb re-joins every argv element into ONE remote command line with spaces,
        # so a bare `>` ends up parsed by the OUTER (unprivileged shell) process -- which sets up file
        # redirection using ITS OWN uid, before run-as's uid transition ever applies to the exec'd
        # inner shell -- not by the inner, run-as-transitioned `sh`. Every well-known workaround
        # routes around this rather than fighting adb's argv-joining: push the payload to a
        # shell-writable staging path first (no BOM -- explicit UTF8Encoding($false), since piping a
        # PowerShell string through `|` to a native process can silently prepend one, which would
        # break the device's strict JSON parser), then have run-as's OWN inner shell perform the
        # `>` redirect as a single quoted command string (so the whole `run-as ... sh -c '...'`
        # sequence survives adb's argv-joining as one unit and the redirect happens under the
        # already-transitioned uid).
        [System.IO.File]::WriteAllText($localTempFile, $json, (New-Object System.Text.UTF8Encoding($false)))
        & $Adb -s $Serial push $localTempFile $sharedTempPathOnDevice 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit "Failed to push $FailureLabel payload to device $Serial." }
        & $Adb -s $Serial shell "run-as $DevDebugPackage sh -c 'cat $sharedTempPathOnDevice > $PayloadPathOnDevice'"
        if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit "Failed to copy $FailureLabel payload into app-private storage on $Serial (is $DevDebugPackage installed and debuggable?)." }
    } finally {
        & $Adb -s $Serial shell rm -f $sharedTempPathOnDevice | Out-Null
        Remove-Item -Force $localTempFile -ErrorAction SilentlyContinue
    }

    # A pre-existing result file from a stale prior attempt is an EXPECTED, non-fatal condition --
    # `rm -f` deliberately never errors on a missing file, so no special handling is needed here.
    & $Adb -s $Serial shell run-as $DevDebugPackage rm -f $ResultPathOnDevice 2>$null | Out-Null
    & $Adb -s $Serial shell am broadcast -n $ReceiverComponent -a $BroadcastAction | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit "Failed to broadcast $FailureLabel trigger to device $Serial." }

    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $raw = & $Adb -s $Serial shell run-as $DevDebugPackage cat $ResultPathOnDevice 2>$null
        if ($raw -and $raw.Trim().StartsWith('{')) {
            & $Adb -s $Serial shell run-as $DevDebugPackage rm -f $ResultPathOnDevice | Out-Null
            try { return ($raw | ConvertFrom-Json) } catch { Write-ErrorAndExit "Malformed $FailureLabel result from device $Serial`: $raw" }
        }
        Start-Sleep -Milliseconds 750
    }
    Write-ErrorAndExit "Timed out waiting for $FailureLabel result from device $Serial (20s)."
}

function Send-PilotPayloadAndTrigger {
    param([string]$Serial, [hashtable]$Payload)
    return Invoke-PilotHarnessOp -Serial $Serial -Payload $Payload -PayloadPathOnDevice $PayloadPath -ResultPathOnDevice $ResultPath `
        -ReceiverComponent $PilotReceiver -BroadcastAction $PilotAction -FailureLabel 'enrollment'
}

function Send-PilotTransportOp {
    param([string]$Serial, [hashtable]$Payload)
    return Invoke-PilotHarnessOp -Serial $Serial -Payload $Payload -PayloadPathOnDevice $TransportPayloadPath -ResultPathOnDevice $TransportResultPath `
        -ReceiverComponent $TransportReceiver -BroadcastAction $TransportAction -FailureLabel 'transport'
}

# --- responsibilities ---

function Invoke-PilotDoctor {
    Write-Section 'Doctor (read-only)'
    & (Join-Path $RepoRoot 'scripts\budcom-services.ps1') -Doctor
    Write-Host "`n-- Android / ADB --"
    if (-not (Test-Path $Adb)) { Write-Warn "adb not found at $Adb"; return }
    Write-Host "adb: $Adb"
    $devices = @(Get-ConnectedDevices)
    Write-Host "Connected devices: $(if ($devices.Count) { $devices -join ', ' } else { '(none)' })"
    if ($devices.Count -lt 2) { Write-Warn "Fewer than two devices attached -- physical enrollment gates cannot run yet." }
    $localProps = Join-Path $RepoRoot 'apps\budcom_android\local.properties'
    Write-Host "local.properties: $(if (Test-Path $localProps) { 'present' } else { 'MISSING -- create it with sdk.dir pointing at the Android SDK before building' })"
    $gradlew = Join-Path $RepoRoot 'apps\budcom_android\gradlew.bat'
    Write-Host "gradlew.bat: $(if (Test-Path $gradlew) { 'present' } else { 'MISSING' })"
}

function Invoke-PilotBootstrap {
    Write-Section 'Bootstrap (idempotent test identities)'
    Invoke-BackendCli -NpmScript 'trust:migrate' -CliArgs @() | Out-Null
    Write-Host '  migrations: applied (idempotent).'
    foreach ($company in $TestCompanies) {
        $created = Get-OrCreateTestBusiness -Company $company
        Write-Host "  [$($company.DisplayName)] businessId=$($created.businessId) membershipId=$($created.membershipId)"
        Set-TestBusinessScope -Company $company -Created $created
    }
    Write-Ok 'Both test companies ensured (business + active membership).'
}

function Invoke-PilotStart {
    Write-Section 'Start Trust + Relay'
    & (Join-Path $RepoRoot 'scripts\budcom-services.ps1') -Start -Verify
    if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit 'Trust/Relay failed to start or verify healthy.' }
    Write-Ok 'Trust + Relay healthy.'
}

function Invoke-PilotEnrollOne {
    param($Company, [string]$Serial)
    Write-Section "Enroll Phone ($Serial) as $($Company.DisplayName)"

    $created = Get-OrCreateTestBusiness -Company $Company
    Set-TestBusinessScope -Company $Company -Created $created | Out-Null

    Write-Host '  building/installing/launching devDebug...'
    # `budcom-android.ps1`'s own internal adb calls print raw text that isn't captured there either;
    # invoking it via `&` runs it in the SAME pipeline context, so without `| Out-Host` that raw text
    # would silently join this function's own return value (turning the final PSCustomObject into
    # part of an array) -- Out-Host displays it immediately instead of passing it further down.
    & (Join-Path $RepoRoot 'scripts\budcom-android.ps1') -DeviceSerial $Serial -Variant DevDebug -Install -Launch -Verify | Out-Host
    if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit "devDebug install/launch/verify failed on $Serial. Run '.\scripts\budcom-android.ps1 -DeviceSerial $Serial -Variant DevDebug -Build' first if no APK has been built yet for the current commit." }

    Write-Host "  adb reverse tcp:$TrustPort / tcp:$RelayPort (USB tunnel -- no LAN IP, no firewall change needed)..."
    & $Adb -s $Serial reverse "tcp:$TrustPort" "tcp:$(Get-HostTrustPort)" | Out-Null
    & $Adb -s $Serial reverse "tcp:$RelayPort" "tcp:$RelayPort" | Out-Null

    $grant = Invoke-BackendCli -NpmScript 'pilot:provision' -CliArgs @('create-enrollment-grant', '--business', $created.businessId, '--actor', $Company.Actor, '--membership', $created.membershipId, '--scope', $FullScope, '--lifetime-ms', '600000')
    if (-not $grant) { Write-ErrorAndExit "create-enrollment-grant returned no parseable result for $($Company.DisplayName)" }
    Write-Host "  fresh one-time enrollment grant issued (grantId=$($grant.grantId), expires $($grant.expiresAt)) -- secret handed to device only, never displayed."

    $payload = @{
        grantId = $grant.grantId
        grantSecret = $grant.grantSecret
        trustBaseUrl = "http://127.0.0.1:$TrustPort/"
        relayBaseUrl = "http://127.0.0.1:$RelayPort/"
    }
    $result = Send-PilotPayloadAndTrigger -Serial $Serial -Payload $payload

    switch ($result.outcome) {
        'success' {
            Write-Ok "$($Company.DisplayName) / $Serial enrolled. businessId=$($result.businessId) deviceId=$($result.deviceId)"
        }
        default {
            Write-ErrorAndExit "Enrollment failed for $($Company.DisplayName) / $Serial`: $($result.outcome)"
        }
    }

    $status = Invoke-BackendCli -NpmScript 'pilot:provision' -CliArgs @('status', '--business', $created.businessId)
    $consumedGrant = $status.enrollmentGrants | Where-Object { $_.grantId -eq $grant.grantId }
    if (-not $consumedGrant -or -not $consumedGrant.consumed -or $consumedGrant.consumedByDeviceId -ne $result.deviceId) {
        Write-ErrorAndExit "Post-enrollment verification failed: grant $($grant.grantId) does not show as consumed by $($result.deviceId) in Trust's own records."
    }
    Write-Ok "Verified via Trust's own records: grant consumed by device $($result.deviceId), business $($created.businessId) active."
    return [PSCustomObject]@{ Company = $Company.DisplayName; BusinessId = $created.businessId; DeviceId = $result.deviceId; Serial = $Serial }
}

function Invoke-PilotEnroll {
    Write-Section 'Enroll both phones'
    $serials = Resolve-PhoneSerials
    $resultA = Invoke-PilotEnrollOne -Company $TestCompanies[0] -Serial $serials.A
    $resultB = Invoke-PilotEnrollOne -Company $TestCompanies[1] -Serial $serials.B
    if ($resultA.BusinessId -eq $resultB.BusinessId -or $resultA.DeviceId -eq $resultB.DeviceId) {
        Write-ErrorAndExit 'Phone A and Phone B ended up sharing a Business or device identity -- this must never happen. Investigate before proceeding.'
    }
    Write-Ok 'Phone A and Phone B hold distinct Business/device identities, each earned through the real enrollment path.'
    return @($resultA, $resultB)
}

function Invoke-PilotVerify {
    Write-Section 'Verify (Trust/Relay health)'
    & (Join-Path $RepoRoot 'scripts\budcom-services.ps1') -Verify
    if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit 'Trust/Relay health check failed.' }
    Write-Ok 'Trust + Relay healthy.'
}

function Invoke-PilotStatus {
    Write-Section 'Controlled-pilot status (human-safe -- no secrets, no credential bodies)'
    foreach ($company in $TestCompanies) {
        $status = Invoke-BackendCli -NpmScript 'pilot:provision' -CliArgs @('status', '--business', (Get-OrCreateTestBusiness -Company $company).businessId) -AllowFailure
        Write-Host "`n$($company.DisplayName)"
        if (-not $status -or -not $status.business) {
            Write-Host '  Business: not yet provisioned'
            continue
        }
        Write-Host "  Business: $($status.business.status) (epoch $($status.business.authorityEpoch))"
        foreach ($m in $status.memberships) {
            Write-Host "  Membership $($m.membershipId): $($m.status), scope=[$($m.authorityScope -join ',')]"
        }
        $consumedGrants = @($status.enrollmentGrants | Where-Object { $_.consumed })
        $pendingGrants = @($status.enrollmentGrants | Where-Object { -not $_.consumed })
        Write-Host "  Enrollment grants: $($consumedGrants.Count) consumed, $($pendingGrants.Count) pending"
        foreach ($g in $consumedGrants) { Write-Host "    Phone enrolled: device $($g.consumedByDeviceId)" }
    }
}

function Get-PilotIdentity {
    param([string]$Serial, [string]$Label)
    $result = Send-PilotTransportOp -Serial $Serial -Payload @{ op = 'get-identity' }
    if ($result.outcome -ne 'success') { Write-ErrorAndExit "get-identity failed on $Label ($Serial): $($result.outcome)" }
    Write-Host "  [$Label] businessId=$($result.businessId) deviceId=$($result.deviceId) (credential fetched, not displayed)"
    return $result
}

function Invoke-PilotBindCounterparty {
    param([string]$Serial, [string]$Label, [string]$PeerDisplayName, $PeerCredential)
    $result = Send-PilotTransportOp -Serial $Serial -Payload @{ op = 'bind-counterparty'; peerDisplayName = $PeerDisplayName; peerCredential = $PeerCredential }
    if ($result.outcome -ne 'success') { Write-ErrorAndExit "bind-counterparty failed on $Label ($Serial): $($result.outcome)" }
    Write-Host "  [$Label] bound counterparty '$PeerDisplayName' -> local partyId=$($result.partyId), status=$($result.bindingStatus)"
    return $result
}

function Invoke-PilotSubmitOrder {
    param([string]$Serial, [string]$Label, [string]$BuyerPartyId, [string]$PeerBusinessId, [string]$CreationKey = $null)
    $payload = @{ op = 'submit-order'; buyerPartyId = $BuyerPartyId; peerBusinessId = $PeerBusinessId; productName = 'Controlled-Pilot Test Product'; quantity = '3' }
    if ($CreationKey) { $payload.creationKey = $CreationKey }
    $result = Send-PilotTransportOp -Serial $Serial -Payload $payload
    $errorSuffix = if (Get-SafeProperty $result 'transportError') { " error=$(Get-SafeProperty $result 'transportError')" } else { '' }
    Write-Host "  [$Label] submit-order outcome=$($result.outcome) orderId=$(Get-SafeProperty $result 'orderId') transportState=$(Get-SafeProperty $result 'transportState')$errorSuffix"
    return $result
}

function Invoke-PilotIngestInbox {
    param([string]$Serial, [string]$Label)
    $result = Send-PilotTransportOp -Serial $Serial -Payload @{ op = 'ingest-inbox' }
    if ($result.outcome -ne 'success') { Write-ErrorAndExit "ingest-inbox failed on $Label ($Serial): $($result.outcome)" }
    $count = @($result.receivedOrders).Count
    Write-Host "  [$Label] ingest-inbox: $count order(s) in structured recipient inbox"
    return $result
}

function Invoke-PilotRunTransport {
    Write-Section 'Real two-phone Relay transport + replay/tamper proof'
    $serials = Resolve-PhoneSerials
    & (Join-Path $RepoRoot 'scripts\budcom-services.ps1') -Verify | Out-Host
    if ($LASTEXITCODE -ne 0) { Write-ErrorAndExit 'Trust/Relay must be healthy before running transport proofs.' }

    # Re-establish the USB reverse tunnel each already-enrolled phone needs to reach Relay and Trust
    # (bind-counterparty performs a live freshness check against Trust's issuer verification
    # key/authority epoch, not just local credential state) -- `adb reverse` bindings do not survive
    # an adb server restart, and unlike enrollment this path never re-provisions credentials, so
    # nothing else in -RunTransport would otherwise repair a stale/missing tunnel. The device side
    # always targets the ORIGINAL enrollment-time port ($TrustPort); the host side follows wherever
    # Trust is actually listening now (BUDCOM_TRUST_PORT, when overridden for this session -- see
    # backend/.env) -- a plain port-translating reverse tunnel, no re-enrollment, no app data,
    # credential, or Business state touched.
    foreach ($serial in @($serials.A, $serials.B)) {
        & $Adb -s $serial reverse "tcp:$RelayPort" "tcp:$RelayPort" | Out-Null
        & $Adb -s $serial reverse "tcp:$TrustPort" "tcp:$(Get-HostTrustPort)" | Out-Null
    }

    Write-Host "`n-- Gate 2: identity discovery --"
    $identityA = Get-PilotIdentity -Serial $serials.A -Label 'Phone A / Test Company 1'
    $identityB = Get-PilotIdentity -Serial $serials.B -Label 'Phone B / Test Company 2'
    if ($identityA.businessId -eq $identityB.businessId -or $identityA.deviceId -eq $identityB.deviceId) {
        Write-ErrorAndExit 'Phone A and Phone B report the same identity -- refusing to proceed.'
    }

    Write-Host "`n-- Counterparty binding (both directions, real Trust-signature verification) --"
    $bindAonB = Invoke-PilotBindCounterparty -Serial $serials.A -Label 'Phone A' -PeerDisplayName 'BUDCOM Test 2 Company' -PeerCredential $identityB.credential
    $bindBonA = Invoke-PilotBindCounterparty -Serial $serials.B -Label 'Phone B' -PeerDisplayName 'BUDCOM Test 1 Company' -PeerCredential $identityA.credential

    Write-Host "`n-- Gate 5 (partial): tampered peer credential must be rejected --"
    $tamperedCredential = $identityB.credential | Select-Object *
    $tamperedCredential.businessId = 'tampered-business-id-does-not-exist'
    $tamperResult = Send-PilotTransportOp -Serial $serials.A -Payload @{ op = 'bind-counterparty'; peerDisplayName = 'Tampered'; peerCredential = $tamperedCredential }
    if ($tamperResult.outcome -eq 'success') { Write-ErrorAndExit 'SECURITY REGRESSION: a tampered peer credential (mismatched businessId) was accepted by verifyAndRecord.' }
    Write-Ok "Tampered credential correctly rejected: $($tamperResult.outcome)"

    Write-Host "`n-- Gate 3/4: A -> Relay -> B --"
    $submitAtoB = Invoke-PilotSubmitOrder -Serial $serials.A -Label 'Phone A' -BuyerPartyId $bindAonB.partyId -PeerBusinessId $identityB.businessId
    if ($submitAtoB.transportState -ne 'RelayAccepted') { Write-ErrorAndExit "A -> Relay submit did not reach RelayAccepted (got $($submitAtoB.transportState))." }
    Start-Sleep -Seconds 2
    $ingestB = Invoke-PilotIngestInbox -Serial $serials.B -Label 'Phone B'
    $receivedOnB = @($ingestB.receivedOrders | Where-Object { $_.orderId -eq $submitAtoB.orderId -and $_.senderBusinessId -eq $identityA.businessId })
    if ($receivedOnB.Count -eq 0) { Write-ErrorAndExit 'Phone B did not receive the order Phone A submitted (or sender identity did not match).' }
    Write-Ok "A -> Relay -> B PASS: order $($submitAtoB.orderId) received on B from correct sender, real Ack sent."

    Write-Host "`n-- Gate 5 (partial): duplicate authenticated Submit (idempotency) --"
    $dupKey = "pilot-replay-test-$([guid]::NewGuid())"
    $first = Invoke-PilotSubmitOrder -Serial $serials.A -Label 'Phone A (replay 1)' -BuyerPartyId $bindAonB.partyId -PeerBusinessId $identityB.businessId -CreationKey $dupKey
    $second = Invoke-PilotSubmitOrder -Serial $serials.A -Label 'Phone A (replay 2)' -BuyerPartyId $bindAonB.partyId -PeerBusinessId $identityB.businessId -CreationKey $dupKey
    $firstOrderId = Get-SafeProperty $first 'orderId'
    $secondOrderId = Get-SafeProperty $second 'orderId'
    if (-not $firstOrderId -or $firstOrderId -ne $secondOrderId) {
        Write-ErrorAndExit "Duplicate Submit with the same creationKey did not resolve to the same order (first=$($first.outcome)/$firstOrderId, second=$($second.outcome)/$secondOrderId) -- idempotency violated."
    }
    Write-Ok "Duplicate Submit correctly idempotent: both calls resolved to order $firstOrderId."

    Write-Host "`n-- Gate 6: B -> Relay -> A --"
    $submitBtoA = Invoke-PilotSubmitOrder -Serial $serials.B -Label 'Phone B' -BuyerPartyId $bindBonA.partyId -PeerBusinessId $identityA.businessId
    if ($submitBtoA.transportState -ne 'RelayAccepted') { Write-ErrorAndExit "B -> Relay submit did not reach RelayAccepted (got $($submitBtoA.transportState))." }
    Start-Sleep -Seconds 2
    $ingestA = Invoke-PilotIngestInbox -Serial $serials.A -Label 'Phone A'
    $receivedOnA = @($ingestA.receivedOrders | Where-Object { $_.orderId -eq $submitBtoA.orderId -and $_.senderBusinessId -eq $identityB.businessId })
    if ($receivedOnA.Count -eq 0) { Write-ErrorAndExit 'Phone A did not receive the order Phone B submitted (or sender identity did not match).' }
    Write-Ok "B -> Relay -> A PASS: order $($submitBtoA.orderId) received on A from correct sender, real Ack sent."

    Write-Host "`nCONTROLLED PILOT TRANSPORT`n"
    Write-Host "Environment:"
    Write-Host "  PostgreSQL / Trust / Relay ... PASS"
    Write-Host "Transport:"
    Write-Host "  A -> B ............ PASS"
    Write-Host "  Ack ............... PASS"
    Write-Host "  B -> A ............ PASS"
    Write-Host "  Ack ............... PASS"
    Write-Host "  Replay rejection .. PASS (duplicate Submit idempotent, tampered credential rejected)"
}

function Invoke-PilotStop {
    Write-Section 'Stop'
    & (Join-Path $RepoRoot 'scripts\budcom-services.ps1') -Stop
}

# --- main ---

Import-DotEnv

if (-not ($Doctor -or $Bootstrap -or $Start -or $Enroll -or $Verify -or $Status -or $Stop -or $RunTransport -or $RunAll)) {
    Write-Host 'No mode selected. Examples:'
    Write-Host '  .\scripts\budcom-controlled-pilot.ps1 -Doctor'
    Write-Host '  .\scripts\budcom-controlled-pilot.ps1 -RunAll'
    exit 0
}

if ($RunAll) {
    Invoke-PilotDoctor
    Invoke-PilotBootstrap
    Invoke-PilotStart
    Invoke-PilotEnroll
    Invoke-PilotVerify
    Invoke-PilotStatus
    exit 0
}

if ($Doctor) { Invoke-PilotDoctor }
if ($Bootstrap) { Invoke-PilotBootstrap }
if ($Start) { Invoke-PilotStart }
if ($Enroll) { Invoke-PilotEnroll }
if ($Verify) { Invoke-PilotVerify }
if ($Status) { Invoke-PilotStatus }
if ($RunTransport) { Invoke-PilotRunTransport }
if ($Stop) { Invoke-PilotStop }
exit 0
