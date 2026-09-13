# Security Decision Matrix

**Scope:** RC#4 Enterprise Security and Local Data Protection Closure Gate  
**Last updated:** 2026-07-25  
**Status:** Controlled pilot reference; unrestricted production not approved.

This matrix records unresolved or partially resolved security decisions that require product-owner authorization before architectural implementation.

| Decision | Threat / data addressed | Current state | Risk if unchanged | Options | Recommended direction | Required before |
|----------|-------------------------|---------------|-------------------|---------|----------------------|-----------------|
| SQLite encryption at rest | Local database theft exposes ledger metadata | Plaintext SQLite at `{databasePath}/venture-ledger.db` | High on shared/unattended machines | SQLCipher; OS DPAPI file encryption; accept plaintext with physical-access controls | Defer architecture; document operator responsibility; revisit for commercial pilot expansion | Commercial pilot expansion |
| Log / audit encryption | File logs and audit JSONL readable locally | Sanitized plaintext logs/audit; bounded rotation (B2a) | Medium — metadata leakage | Encrypt log files; restrict ACLs; remote sink | OS ACL + existing sanitization for controlled pilot | Unrestricted production |
| OS credential vault for secrets | Token/licence theft from disk | No licence vault in MVP; config schema has no secret fields | Low today (no secrets persisted) | Windows Credential Manager; DPAPI wrapper | Implement when licence/token storage is approved | Licensing milestone |
| Licence secret storage | Licence key exposure | Not implemented | N/A until licensing ships | Vault vs encrypted file vs online activation | Blocked on licensing spec | Licensing milestone |
| Code signing (desktop/installer) | Tampered binaries | Unsigned dev builds | Medium for wide distribution | EV/OV signing; internal signing | Release decision per ADR accepted hardening | Unrestricted production |
| Automatic update trust | Supply-chain tampering | No updater | Medium once updates ship | Signed delta packages; staged rollout | Defer until updater milestone | Unrestricted production |
| Backup encryption | Backup file theft | Manual plaintext `.db` copies | Medium | Encrypted backup format; operator-managed encryption | Document operator ownership (B2-doc); no auto-delete | Commercial pilot |
| Secure deletion | Forensic recovery of deleted data | Standard filesystem delete | Low for MVP scope | Secure wipe utility; encrypted overwrite | Defer — not MVP scope | Unrestricted production |
| Crash reporting / telemetry | Blind production defects vs privacy | Disabled | Medium operational visibility | Opt-in Sentry-style with allowlist | Explicit owner opt-in required | Post-MVP |
| Remote support access | Support without on-site visit | Diagnostics export only (allowlisted) | Low with export policy | Remote bundle upload; screen share | Manual export remains approved path | Controlled pilot OK |
| LAN API authentication (TD-009) | Unauthenticated connector on LAN | Loopback default; LAN requires acknowledgement; no authN | High if LAN enabled carelessly | Local token; mTLS; VPN-only | Keep loopback default; document firewall requirement | Commercial pilot if LAN required |
| Inbound offline XML envelope hardening | Malicious imported XML files | Unified envelope boundary (`InboundXmlEnvelopeService`); import-attempt history; domain upsert deferred | Medium for import modes | Offline domain persistence + desktop import UI | Implemented in RC#4 §30; commercial pilot after domain upsert | Commercial pilot |
| Controlled-pilot release engineering | Tampered artifacts; packaging leaks; unsafe upgrade | Release mode contract; build metadata; packaging boundary inspection; SHA-256 manifest; NSIS config with `deleteAppDataOnUninstall: false`; manual upgrade only | Medium — unsigned pilot distribution | EV/OV signing; authenticated updater | Checksum-verified manual distribution for controlled pilot | Controlled pilot distribution |
| Release-mode enforcement | Production capabilities in pilot | Typed `VENTURE_RELEASE_MODE`; fail-closed unknown values; diagnostics expose mode | Medium if misconfigured | Build-time injection | Implemented §31 | Controlled pilot |
| Controlled-pilot lifecycle gate (§32) | Installer substitution; orphan connector; AppData deletion on uninstall; cleanup path escape | SHA-256 preflight; real Windows install/reinstall/uninstall/reinstall harness; `deleteAppDataOnUninstall: false` proven; marker-guarded cleanup; lifecycle unit tests | Medium — unsigned SmartScreen friction; health timeout on slow hosts | VM/Sandbox isolation; signed installer | Checksum-verified lifecycle gate before pilot handoff | Controlled pilot distribution |

## Controlled pilot status

Approved with documented limitations:

- Loopback-first connector binding with production LAN acknowledgement gate
- Fail-closed Tally egress (IMPORT/EXECUTE denied)
- IPC allowlist + bounded payloads + owned export path containment
- Parameterized SQL + company-scoped API negative tests
- Privacy-safe diagnostics and log sanitization
- Local lifecycle documentation without backup auto-deletion
- **Manual controlled-pilot distribution with SHA-256 checksum verification**
- **Unsigned installer/portable builds with explicit SmartScreen guidance**
- **Real Windows uninstall-retention proof (§32)** — NSIS uninstall removes binaries; `%APPDATA%/@venture/desktop/` retained
- **Lifecycle validation harness** — `scripts/lifecycle/*` with fail-closed integrity checks

## Commercial pilot blockers

- TD-009 authenticated LAN access (if non-loopback deployment required)
- SQLite encryption decision (if hosting on shared machines)
- Code signing for distributed desktop installer
- Formal third-party penetration test (optional but recommended)

## Unrestricted production blockers

All commercial pilot blockers, plus:

- Encryption at rest policy for SQLite, logs, and backups
- Installer/uninstall lifecycle automation with explicit data ownership
- Crash reporting policy (if desired)
- Auto-update trust chain
- Second-instance focus behaviour on real Windows (harness documents policy only)
- Connector `/health` readiness within fixed gate timeout on all hosts
- Genuine earlier signed pilot installer upgrade path (schema fixture used instead)

## Related documents

- [connector-security-audit.md](../audits/connector-security-audit.md)
- [desktop-security-review.md](./desktop-security-review.md)
- [ADR-004-fail-closed-security.md](../architecture/adr/ADR-004-fail-closed-security.md)
- [local-data-lifecycle.md](../operations/local-data-lifecycle.md)
- [milestone-5b-stage-update.md](../stage-updates/milestone-5b-stage-update.md) §29
