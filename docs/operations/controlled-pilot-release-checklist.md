# Controlled-pilot release acceptance checklist

Use before distributing a controlled-pilot build.

## Source and identity

- [ ] **Clean working tree** (`dirtyTree=false`) for pilot-distribution builds
- [ ] Pre-commit gate runs with `dirtyTree=true` are **evidence only** — not for distribution
- [ ] After commit and push, rerun release pipeline from clean tree before handoff
- [ ] Correct git commit recorded in build metadata matches the release commit
- [ ] Desktop and connector package versions match build metadata
- [ ] `BUDCOM_RELEASE_MODE=controlled_pilot`
- [ ] Storage schema version recorded

## Packaging boundary

- [ ] Package inspection passed (`scripts/release/package-boundary.mjs`)
- [ ] No `.env`, database, log, fixture, test, or private-key files in artifact
- [ ] No source maps unless explicitly approved
- [ ] No customer data or local config included

## Security posture

- [ ] No Tally write routes
- [ ] No XML upload HTTP route
- [ ] No LAN exposure enabled by default
- [ ] Auto-update disabled
- [ ] Child process environment minimized
- [ ] Firewall rules target the exact bundled Node: TCP 8080/8443 and UDP 5353, Private/Public profiles, `LocalSubnet` only
- [ ] Upgrade replaces stale Budcom firewall rules and uninstall removes them

## Integrity

- [ ] SHA-256 checksum generated after final artifact creation
- [ ] Manifest generated
- [ ] Manifest verification passed
- [ ] Artifact size recorded

## Behaviour

- [ ] Upgrade path tested on representative machine
- [ ] Uninstall retains `%APPDATA%/@budcom/desktop/` data by default
- [ ] Migration failure blocks normal startup
- [ ] Future schema version fails closed
- [ ] Single desktop instance enforced
- [ ] Connector ownership verified
- [ ] Every mutable Connector path is under Budcom AppData and outside install/resources
- [ ] Transport fingerprint is identical across same-version reinstall and supported upgrade
- [ ] Packaged child failures retain bounded sanitized stderr
- [ ] Packaged build reports exact clean-commit controlled-pilot provenance, never development fallback
- [ ] Packaged `/health` and `/companies` succeed against physical Tally before Android testing

## Lifecycle gate (RC#4 §32)

- [ ] Candidate SHA-256 verified before install (`node scripts/lifecycle/candidate-integrity.mjs`)
- [ ] Real Windows lifecycle harness executed (`node scripts/lifecycle/lifecycle-gate-runner.mjs --execute-windows`)
- [ ] Uninstall-retention proven on real Windows (binaries removed; AppData retained)
- [ ] Reinstall-after-uninstall reconnects to retained data
- [ ] Lifecycle unit tests pass (`apps/budcom_desktop/test/unit/lifecycle-gate.test.ts`)
- [ ] Lifecycle report archived under `release/controlled-pilot/<version>/reports/lifecycle-gate-report.json`

## Validation

- [ ] Connector lint/build/tests/architecture/contract/audit pass
- [ ] Desktop lint/build/tests/audit pass
- [ ] Release command pass report archived

## Limitations recorded

- [ ] Unsigned build limitation documented
- [ ] SmartScreen guidance documented
- [ ] Unrestricted production remains blocked
- [ ] Code signing blocker recorded
- [ ] Authenticated update trust blocker recorded
