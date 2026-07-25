# Controlled-pilot installation runbook

**Status:** Controlled pilot — unsigned builds permitted with explicit limitation  
**Last updated:** 2026-07-25

## Supported environment

- Windows 10 or later (64-bit x64)
- Local loopback connector only — no LAN exposure
- Tally must be reachable locally by the operator

## Obtain the artifact

1. Download the artifact only from the approved controlled distribution source provided by the Budcom operator.
2. Note the exact filename, for example `BudcomDesktop-0.4.3-x64-portable.zip` or `BudcomDesktop-0.4.3-x64-setup.exe`.
3. Download the matching `SHA256SUMS.txt` and `artifacts.manifest.json` from the same source.

## Verify SHA-256 integrity

PowerShell:

```powershell
Get-FileHash .\BudcomDesktop-0.4.3-x64-portable.zip -Algorithm SHA256
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
2. Accept per-user installation (no administrator rights required).
3. Do not expect automatic launch after install (`runAfterFinish: false`).

## First start

1. Application data is created under `%APPDATA%/budcom-desktop/`.
2. Connector data is stored under `%APPDATA%/budcom-desktop/connector-data/`.
3. Confirm release mode in startup logs or diagnostics export: `controlled_pilot`.
4. Confirm desktop and connector versions in diagnostics metadata.
5. Connector starts on loopback only.

## Backup before upgrade

Before upgrading:

1. Export diagnostics if support needs context.
2. Copy `%APPDATA%/budcom-desktop/connector-data/` including `budcom-ledger.db` and any `backups/` directory.
3. Copy `%APPDATA%/budcom-desktop/desktop-config.json`.

## Manual upgrade

1. Close the desktop application gracefully.
2. Verify the new artifact checksum.
3. Install or extract the new version.
4. Restart and confirm versions and release mode.

Automatic update is disabled in controlled pilot.

## Uninstall

- Uninstall removes application binaries only.
- Configuration, SQLite database, logs, diagnostic exports, and backups remain under `%APPDATA%/budcom-desktop/` unless the operator deletes them manually.
- No destructive data-delete option is provided in controlled pilot.

## Diagnostics and privacy

1. Use the in-app diagnostics export feature.
2. Review exported bundle locally before sharing.
3. Do not share bundles containing operator-chosen export paths unless redaction has been verified.

## Rollback limitation

Downgrading to an older application against a newer database schema is blocked at connector startup. Restore from backup instead of forcing downgrade.

## Escalation

Escalate through the operator's approved Budcom support channel with:

- artifact filename
- verified commit hash from diagnostics
- release mode
- diagnostics export (after local privacy review)
