# Controlled-pilot installation runbook

**Status:** Controlled pilot — unsigned builds permitted with explicit limitation  
**Last updated:** 2026-07-25 (lifecycle gate §32)

## Supported environment

- Windows 10 or later (64-bit x64)
- Local loopback connector only — no LAN exposure
- Tally must be reachable locally by the operator

## Obtain the artifact

1. Download the artifact only from the approved controlled distribution source provided by the Venture operator.
2. Note the exact filename, for example `VentureDesktop-0.4.3-x64-portable.zip` or `VentureDesktop-0.4.3-x64-setup.exe`.
3. Download the matching `SHA256SUMS.txt` and `artifacts.manifest.json` from the same source.

## Verify SHA-256 integrity

PowerShell:

```powershell
Get-FileHash .\VentureDesktop-0.4.3-x64-portable.zip -Algorithm SHA256
```

Compare the hash to the value in `SHA256SUMS.txt`.

Alternatively:

```powershell
node scripts/release/manifest.mjs verify .\artifacts .\manifest\artifacts.manifest.json
```

## Windows SmartScreen (unsigned pilot build)

Controlled-pilot builds may be unsigned. Windows SmartScreen may warn that the publisher is unknown.

- Do **not** disable antivirus or Windows security.
- Continue only if the checksum matches the controlled distribution source.
- If the checksum does not match, stop and report the mismatch.

## Install or extract

Portable pilot layout:

1. Extract the portable archive to a local directory owned by the operator.
2. Launch the desktop shell from the packaged `desktop/dist/main/main.js` via Electron runtime bundled in the installer when using NSIS build.

NSIS installer (when produced on Windows):

1. Run the setup executable.
2. Approve the system-wide installation prompt. Elevation is required for Program Files and the narrowly scoped Venture Connector firewall rules.
3. Do not expect automatic launch after install (`runAfterFinish: false`).

### Legacy transport identity during over-install

Before electron-builder runs the old uninstaller, the installer checks the resolved installation
path and the registered HKLM/per-machine and HKCU/per-user installation paths for the historical
`resources\connector\dist\data\transport` identity. A complete legacy certificate/key pair is
copied and byte-for-byte verified in a staging directory beside the persistent AppData identity, then
published by an atomic directory rename. The application must not be launched until the persistent
pair and its SPKI fingerprint have been verified.

- A complete persistent pair is authoritative and is never overwritten.
- A partial persistent or legacy pair aborts installation before the old tree is replaced.
- Multiple identical legacy pairs are accepted; different pairs abort as ambiguous.
- Copy, hash, or atomic-publication failure aborts installation and removes only installer-created
  staging material.
- Runtime TD-018 migration remains a secondary compatibility fallback for non-NSIS layouts.

## First start

1. Application data is created under `%APPDATA%/@venture/desktop/` (Electron `userData` for package `@venture/desktop`).
2. Connector data is stored under `%APPDATA%/@venture/desktop/connector-data/`.
3. Connector transport identity is stored under `%APPDATA%/@venture/desktop/connector-transport-identity/`.
4. Tally request audit output is stored under `%APPDATA%/@venture/desktop/connector-diagnostics/`; no mutable Connector file may be written beneath Program Files.
3. Confirm release mode in startup logs or diagnostics export: `controlled_pilot`.
4. Confirm desktop and connector versions in diagnostics metadata.
5. Connector starts on loopback only.

## Backup before upgrade

Before upgrading:

1. Export diagnostics if support needs context.
2. Copy `%APPDATA%/@venture/desktop/connector-data/` including `venture-ledger.db` and any `backups/` directory.
3. Copy `%APPDATA%/@venture/desktop/desktop-config.json`.
4. Back up `%APPDATA%/@venture/desktop/connector-transport-identity/` securely; never share its private key.

## Manual upgrade

1. Close the desktop application gracefully.
2. Verify the new artifact checksum.
3. Install or extract the new version.
4. Restart and confirm versions and release mode.

Automatic update is disabled in controlled pilot.

## Uninstall

- Uninstall removes application binaries only.
- Configuration, SQLite database, logs, diagnostic exports, and backups remain under `%APPDATA%/@venture/desktop/` unless the operator deletes them manually.
- No destructive data-delete option is provided in controlled pilot.
- Uninstall removes the stable-name Venture HTTP, HTTPS, and mDNS firewall rules. Upgrade replaces them so they target the current bundled `resources/node/node.exe` path.

## Diagnostics and privacy

1. Use the in-app diagnostics export feature.
2. Review exported bundle locally before sharing.
3. Do not share bundles containing operator-chosen export paths unless redaction has been verified.

## Rollback limitation

Downgrading to an older application against a newer database schema is blocked at connector startup. Restore from backup instead of forcing downgrade.

## Lifecycle validation harness (operators and release engineers)

Preflight integrity (no install):

```powershell
node scripts/lifecycle/candidate-integrity.mjs
```

Dry-run report skeleton:

```powershell
node scripts/lifecycle/lifecycle-gate-runner.mjs
```

Real Windows lifecycle gate (isolated profile or first-install session only):

```powershell
node scripts/lifecycle/lifecycle-gate-runner.mjs --execute-windows
```

Real pre-launch identity over-install gate (isolated disposable Windows profile only):

```powershell
$env:VENTURE_LIFECYCLE_ISOLATED_PROFILE='1'
$env:VENTURE_LIFECYCLE_OLD_INSTALLER='<exact old installer path>'
$env:VENTURE_LIFECYCLE_LEGACY_IDENTITY_DIR='<complete test identity fixture directory>'
$env:VENTURE_LIFECYCLE_OLD_INSTALL_SCOPE='allusers' # or currentuser
node scripts/lifecycle/lifecycle-gate-runner.mjs --execute-windows-identity-overinstall
```

This mode installs the old product, seeds its legacy resource-tree identity, runs the candidate's
normal uninstall-first over-install, and verifies both byte hashes and the SPKI fingerprint before
any Desktop or Connector process is allowed to start. Use the candidate itself as the old installer
for the same-version case, an approved older installer for upgrade coverage, and a per-user-capable
older installer with `currentuser` for the per-user-to-Program-Files case.

Bounded cleanup of gate-created data (marker-guarded):

```powershell
node scripts/lifecycle/lifecycle-gate-runner.mjs --cleanup-only
```

Report output: `release/controlled-pilot/<version>/reports/lifecycle-gate-report.json`

## Escalation

Escalate through the operator's approved Venture support channel with:

- artifact filename
- verified commit hash from diagnostics
- release mode
- diagnostics export (after local privacy review)
